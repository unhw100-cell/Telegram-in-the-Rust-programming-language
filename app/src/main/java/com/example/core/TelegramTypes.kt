package com.example.core

enum class TransportProtocol(val label: String, val markerHex: String, val description: String) {
    ABRIDGED("Abridged", "0xEF", "1-byte / 4-byte length prefix (lowest overhead for mobile)"),
    INTERMEDIATE("Intermediate", "0xEEEEEEEE", "4-byte little-endian length prefix"),
    PADDED_INTERMEDIATE("Padded", "0xDDDDDDDD", "Intermediate with random 0-15 byte padding (anti-DPI)")
}

data class DataCenter(
    val id: Int,
    val name: String,
    val ip: String,
    val port: Int = 443,
    val location: String,
    val isTest: Boolean = false
) {
    val endpoint: String get() = "$ip:$port"
}

val TELEGRAM_DCS = listOf(
    DataCenter(1, "DC 1 (Pluto)", "149.154.167.40", 443, "Miami, USA"),
    DataCenter(2, "DC 2 (Venus)", "149.154.167.50", 443, "Amsterdam, NL (Default EU)"),
    DataCenter(3, "DC 3 (Aurora)", "149.154.167.51", 443, "Miami, USA"),
    DataCenter(4, "DC 4 (Vesta)", "149.154.167.91", 443, "Amsterdam, NL"),
    DataCenter(5, "DC 5 (Flora)", "91.108.56.165", 443, "Singapore, SG")
)

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val endpoint: String) : ConnectionState()
    data class Handshaking(val stage: String) : ConnectionState()
    data class Connected(val endpoint: String, val authKeyId: String, val latencyMs: Long) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class PacketRecord(
    val id: Long = System.currentTimeMillis(),
    val direction: Direction,
    val sizeBytes: Int,
    val hexPreview: String,
    val description: String,
    val timestampMs: Long = System.currentTimeMillis()
) {
    enum class Direction { INBOUND, OUTBOUND }
}

data class CachedMessage(
    val id: Long,
    val peerId: Long,
    val senderId: Long,
    val body: String,
    val timestamp: Long,
    val deliveryStatus: Int // 0: Sending, 1: Sent, 2: Read
)

sealed class CoreError(val messageText: String) : Exception(messageText) {
    class Network(val reason: String) : CoreError("Network I/O failed: $reason")
    class Storage(val reason: String) : CoreError("Storage failure: $reason")
    class Crypto(val reason: String) : CoreError("Cryptography error: $reason")
    class IllegalState(val reason: String) : CoreError("Invalid state transition: $reason")
    class InternalPanic(val context: String, val reason: String) : CoreError("Critical Rust Panic in [$context]: $reason")
}

data class PanicTestResult(
    val scenario: String,
    val caughtPanic: Boolean,
    val context: String,
    val errorReason: String,
    val jvmSurvived: Boolean = true,
    val executionTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class AuthKeySession(
    val keyId: String,
    val rawKeyHex: String,
    val isZeroized: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
