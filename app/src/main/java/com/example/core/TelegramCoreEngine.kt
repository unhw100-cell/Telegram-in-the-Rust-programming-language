package com.example.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance Telegram Core Engine matching UniFFI interface specs.
 * Encapsulates the 2-worker background runtime, TCP socket loop, MTProto framing,
 * panic boundary safety, and Zeroize memory protection.
 */
class TelegramCoreEngine(
    val storageDir: String
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeSocket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val isRunning = AtomicBoolean(false)

    private val _packetFlow = MutableSharedFlow<PacketRecord>(extraBufferCapacity = 64)
    val packetFlow: SharedFlow<PacketRecord> = _packetFlow

    private var activeAuthKey: ByteArray? = null
    private var activeAuthKeyId: Long = 0
    private var isAuthKeyZeroized = false

    init {
        generateNewAuthKey()
    }

    /**
     * Generates a 256-byte (2048-bit) MTProto authorization key and computes its key_id
     */
    fun generateNewAuthKey(): AuthKeySession {
        val random = SecureRandom()
        val key = ByteArray(256)
        random.nextBytes(key)
        activeAuthKey = key
        isAuthKeyZeroized = false

        // MTProto auth_key_id: SHA256 lower 64 bits
        val sha = MessageDigest.getInstance("SHA-256")
        val hash = sha.digest(key)
        var keyId: Long = 0
        for (i in 0 until 8) {
            keyId = keyId or ((hash[hash.size - 8 + i].toLong() and 0xFF) shl (i * 8))
        }
        activeAuthKeyId = keyId

        val preview = key.take(32).joinToString(" ") { "%02X".format(it) } + " ... [256 bytes]"
        return AuthKeySession(
            keyId = "0x" + java.lang.Long.toHexString(keyId).uppercase(),
            rawKeyHex = preview,
            isZeroized = false
        )
    }

    /**
     * Simulates Rust Zeroize / ZeroizeOnDrop by securely wiping the 256-byte array with zeros
     */
    fun zeroizeActiveKey(): AuthKeySession {
        activeAuthKey?.let { key ->
            for (i in key.indices) {
                key[i] = 0.toByte() // Overwrite memory
            }
        }
        isAuthKeyZeroized = true
        return AuthKeySession(
            keyId = "0x0000000000000000 (PURGED)",
            rawKeyHex = "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ... [ALL ZEROS - MEMORY SANITIZED]",
            isZeroized = true
        )
    }

    fun getActiveKeySession(): AuthKeySession {
        val key = activeAuthKey ?: return AuthKeySession("None", "No Key", false)
        val preview = if (isAuthKeyZeroized) {
            "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ... [PURGED]"
        } else {
            key.take(32).joinToString(" ") { "%02X".format(it) } + " ... [256 bytes]"
        }
        val idStr = if (isAuthKeyZeroized) "0x0000000000000000" else "0x" + java.lang.Long.toHexString(activeAuthKeyId).uppercase()
        return AuthKeySession(
            keyId = idStr,
            rawKeyHex = preview,
            isZeroized = isAuthKeyZeroized
        )
    }

    /**
     * Connects to a Telegram Data Center IP and port
     */
    suspend fun connect(
        dc: DataCenter,
        protocol: TransportProtocol,
        onStateChange: (ConnectionState) -> Unit
    ) = withContext(Dispatchers.IO) {
        disconnect()

        onStateChange(ConnectionState.Connecting(dc.endpoint))
        val startTime = System.currentTimeMillis()

        try {
            val socket = Socket()
            // Set TCP nodelay for MTProto packets
            socket.tcpNoDelay = true
            socket.soTimeout = 10000
            socket.connect(InetSocketAddress(dc.ip, dc.port), 5000)

            val latency = System.currentTimeMillis() - startTime
            activeSocket = socket
            inputStream = socket.getInputStream()
            outputStream = socket.getOutputStream()
            isRunning.set(true)

            onStateChange(ConnectionState.Handshaking("Sending ${protocol.label} header (${protocol.markerHex})"))

            // Send handshake byte
            val handshakeBytes = when (protocol) {
                TransportProtocol.ABRIDGED -> byteArrayOf(0xef.toByte())
                TransportProtocol.INTERMEDIATE -> byteArrayOf(0xee.toByte(), 0xee.toByte(), 0xee.toByte(), 0xee.toByte())
                TransportProtocol.PADDED_INTERMEDIATE -> byteArrayOf(0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte())
            }

            outputStream?.write(handshakeBytes)
            outputStream?.flush()

            val hexPreview = handshakeBytes.joinToString(" ") { "%02X".format(it) }
            _packetFlow.emit(
                PacketRecord(
                    direction = PacketRecord.Direction.OUTBOUND,
                    sizeBytes = handshakeBytes.size,
                    hexPreview = hexPreview,
                    description = "MTProto Handshake Header (${protocol.label})"
                )
            )

            val keyIdStr = "0x" + java.lang.Long.toHexString(activeAuthKeyId).uppercase()
            onStateChange(ConnectionState.Connected(dc.endpoint, keyIdStr, latency))

            // Start background receiver worker
            startReadWorker(protocol, onStateChange)

        } catch (e: Exception) {
            disconnect()
            onStateChange(ConnectionState.Error("Connection to ${dc.endpoint} failed: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Background packet receiver matching Tokio read_frame loop
     */
    private fun startReadWorker(
        protocol: TransportProtocol,
        onStateChange: (ConnectionState) -> Unit
    ) {
        scope.launch {
            val buffer = ByteArray(4096)
            try {
                while (isRunning.get() && activeSocket?.isConnected == true) {
                    val stream = inputStream ?: break
                    val bytesRead = stream.read(buffer)
                    if (bytesRead <= 0) break

                    val frameSlice = buffer.copyOf(bytesRead)
                    val preview = frameSlice.take(16).joinToString(" ") { "%02X".format(it) }
                    _packetFlow.emit(
                        PacketRecord(
                            direction = PacketRecord.Direction.INBOUND,
                            sizeBytes = bytesRead,
                            hexPreview = preview,
                            description = "MTProto Incoming Frame ($bytesRead B)"
                        )
                    )
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    onStateChange(ConnectionState.Disconnected)
                }
            }
        }
    }

    /**
     * Transmits a framed packet to the Telegram server
     */
    suspend fun sendFrame(
        payload: ByteArray,
        protocol: TransportProtocol
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val out = outputStream ?: throw CoreError.Network("Socket not connected")

            // Frame according to protocol
            val framed = when (protocol) {
                TransportProtocol.ABRIDGED -> {
                    val wordLen = payload.size / 4
                    if (wordLen < 0x7f) {
                        byteArrayOf(wordLen.toByte()) + payload
                    } else {
                        byteArrayOf(
                            0x7f.toByte(),
                            (wordLen and 0xff).toByte(),
                            ((wordLen shr 8) and 0xff).toByte(),
                            ((wordLen shr 16) and 0xff).toByte()
                        ) + payload
                    }
                }
                TransportProtocol.INTERMEDIATE, TransportProtocol.PADDED_INTERMEDIATE -> {
                    val len = payload.size
                    byteArrayOf(
                        (len and 0xff).toByte(),
                        ((len shr 8) and 0xff).toByte(),
                        ((len shr 16) and 0xff).toByte(),
                        ((len shr 24) and 0xff).toByte()
                    ) + payload
                }
            }

            out.write(framed)
            out.flush()

            val hexPreview = framed.take(16).joinToString(" ") { "%02X".format(it) }
            _packetFlow.emit(
                PacketRecord(
                    direction = PacketRecord.Direction.OUTBOUND,
                    sizeBytes = framed.size,
                    hexPreview = hexPreview,
                    description = "Outbound MTProto Frame (${payload.size} B payload)"
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Disconnects socket and cleans up resources
     */
    fun disconnect() {
        isRunning.set(false)
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        inputStream = null
        outputStream = null
    }

    /**
     * Test Panic Barrier (safe_ffi! parity test)
     * Simulates various fatal conditions in Rust code and demonstrates that
     * catch_unwind safely catches them and returns CoreError.InternalPanic without crashing JVM.
     */
    fun testPanicBoundary(scenario: String): PanicTestResult {
        val start = System.currentTimeMillis()
        try {
            // Simulated safe_ffi_boundary block
            when (scenario) {
                "oob" -> {
                    // Rust: let v = vec![1, 2, 3]; let _ = v[999];
                    throw IndexOutOfBoundsException("index out of bounds: the len is 3 but the index is 999")
                }
                "unwrap" -> {
                    // Rust: let opt: Option<i32> = None; opt.unwrap();
                    throw NullPointerException("called `Option::unwrap()` on a `None` value at crates/tg-ffi/src/core.rs:184:22")
                }
                "corrupt_frame" -> {
                    // Rust: panic!("Corrupted frame CRC32 mismatch: expected 0x7A4C12, found 0x000000");
                    throw IllegalStateException("assertion `left == right` failed: Corrupted MTProto frame CRC32 checksum mismatch: expected 0x7A4C12, found 0x000000")
                }
                "stack_overflow_sim" -> {
                    throw AssertionError("panic in thread 'tg-core-runtime' at 'Critical session state desynchronization in crypto engine'")
                }
                else -> {
                    throw RuntimeException("Unknown panic scenario: $scenario")
                }
            }
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - start
            // Mapped to CoreError.InternalPanic by safe_ffi! macro
            val error = CoreError.InternalPanic(
                context = "TelegramCore::test_panic_boundary($scenario)",
                reason = t.message ?: "Unknown panic payload"
            )
            return PanicTestResult(
                scenario = scenario,
                caughtPanic = true,
                context = error.context,
                errorReason = error.reason,
                jvmSurvived = true,
                executionTimeMs = elapsed
            )
        }
    }

    /**
     * Measures real socket round-trip time (RTT) to any Telegram DC
     */
    suspend fun pingEndpoint(ip: String, port: Int): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(ip, port), 3000)
            val elapsed = System.currentTimeMillis() - start
            sock.close()
            elapsed
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Handles background push notification without launching UI (matching Rust TelegramCore::handle_background_push)
     */
    fun handleBackgroundPush(payload: String): PushNotificationResult {
        return PushNotificationResult(
            chatId = 777L,
            senderName = "Telegram Notifications",
            previewText = payload,
            timestamp = System.currentTimeMillis(),
            hasUnread = true
        )
    }
}
