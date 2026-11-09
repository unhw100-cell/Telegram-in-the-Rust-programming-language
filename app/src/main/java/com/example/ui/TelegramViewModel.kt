package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.*
import com.example.data.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val selectedDc: DataCenter = TELEGRAM_DCS[1], // DC 2 Amsterdam (Default)
    val selectedProtocol: TransportProtocol = TransportProtocol.ABRIDGED,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val packets: List<PacketRecord> = emptyList(),
    val activeKeySession: AuthKeySession = AuthKeySession("None", "No Key", false),
    val panicResults: List<PanicTestResult> = emptyList(),
    val chats: List<ChatDto> = emptyList(),
    val selectedChatId: Long = 777L,
    val selectedChatMessages: List<MessageDto> = emptyList(),
    val messageSearchQuery: String = "",
    val benchmarkResult: MessageRepository.BenchmarkResult? = null,
    val logs: List<LogEntry> = emptyList(),
    val customIp: String = "149.154.167.50",
    val customPort: String = "443",
    val isCustomDc: Boolean = false,
    val selectedRustFile: String = "crates/tg-core/src/lib.rs",
    val pingMs: Long = -1L,
    val isPinging: Boolean = false
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Level,
    val tag: String,
    val message: String
) {
    enum class Level { INFO, DEBUG, WARN, ERROR, PANIC_CAUGHT }
}

class TelegramViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = TelegramCoreEngine(application.filesDir.absolutePath)
    private val repository = MessageRepository(application)

    private val _uiState = MutableStateFlow(
        UiState(activeKeySession = engine.getActiveKeySession())
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        log(LogEntry.Level.INFO, "tg_core", "TelegramCore initialized with 2-worker Tokio thread pool.")
        log(LogEntry.Level.INFO, "tg_crypto", "AuthKey generated (2048-bit). ZeroizeOnDrop guard active.")
        log(LogEntry.Level.INFO, "tg_storage", "SQLite WAL opened at ${application.filesDir.absolutePath}/tg_database.db (tables: chats, messages, users)")

        // Observe incoming packets
        viewModelScope.launch {
            engine.packetFlow.collect { packet ->
                _uiState.value = _uiState.value.copy(
                    packets = listOf(packet) + _uiState.value.packets.take(49)
                )
                log(
                    LogEntry.Level.DEBUG,
                    "tg_network",
                    "${packet.direction}: ${packet.description} [${packet.sizeBytes} B]"
                )

                // Если пришел входящий фрейм, сохраняем его в чат Telegram Notifications
                if (packet.direction == PacketRecord.Direction.INBOUND) {
                    val incoming = MessageDto(
                        id = System.currentTimeMillis(),
                        chatId = 777L,
                        senderId = 777L,
                        text = "Входящий MTProto фрейм: ${packet.hexPreview} (${packet.sizeBytes} байт)",
                        timestamp = System.currentTimeMillis(),
                        isOutgoing = false,
                        deliveryStatus = 2
                    )
                    repository.saveMessage(incoming)
                    refreshStorage()
                }
            }
        }

        refreshStorage()
    }

    fun selectDc(dc: DataCenter) {
        _uiState.value = _uiState.value.copy(selectedDc = dc, isCustomDc = false)
        log(LogEntry.Level.INFO, "tg_core", "Selected target Data Center: ${dc.name} (${dc.endpoint})")
    }

    fun setCustomDc(ip: String, port: String) {
        val portInt = port.toIntOrNull() ?: 443
        val custom = DataCenter(99, "Custom DC", ip, portInt, "User Defined Endpoint")
        _uiState.value = _uiState.value.copy(
            selectedDc = custom,
            customIp = ip,
            customPort = port,
            isCustomDc = true
        )
    }

    fun selectProtocol(protocol: TransportProtocol) {
        _uiState.value = _uiState.value.copy(selectedProtocol = protocol)
        log(LogEntry.Level.INFO, "tg_network", "Transport protocol set to: ${protocol.label} (${protocol.markerHex})")
    }

    fun connect() {
        val dc = _uiState.value.selectedDc
        val protocol = _uiState.value.selectedProtocol
        log(LogEntry.Level.INFO, "tg_network", "Initiating TCP handshake to ${dc.endpoint} via ${protocol.label}...")

        viewModelScope.launch {
            engine.connect(dc, protocol) { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
                when (state) {
                    is ConnectionState.Connected -> {
                        log(LogEntry.Level.INFO, "tg_network", "MTProto connected to ${state.endpoint}! RTT latency: ${state.latencyMs} ms")

                        val msg = MessageDto(
                            id = System.currentTimeMillis(),
                            chatId = 777L,
                            senderId = 777L,
                            text = "Сессия MTProto установлена с ${dc.name} (${dc.endpoint})",
                            timestamp = System.currentTimeMillis(),
                            isOutgoing = false,
                            deliveryStatus = 2
                        )
                        viewModelScope.launch {
                            repository.saveMessage(msg)
                            refreshStorage()
                        }
                    }
                    is ConnectionState.Error -> {
                        log(LogEntry.Level.ERROR, "tg_network", state.message)
                    }
                    is ConnectionState.Disconnected -> {
                        log(LogEntry.Level.WARN, "tg_network", "Connection disconnected.")
                    }
                    else -> {}
                }
            }
        }
    }

    fun disconnect() {
        engine.disconnect()
        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Disconnected)
        log(LogEntry.Level.INFO, "tg_network", "Socket closed by user.")
    }

    fun pingTarget() {
        val dc = _uiState.value.selectedDc
        _uiState.value = _uiState.value.copy(isPinging = true)
        viewModelScope.launch {
            val rtt = engine.pingEndpoint(dc.ip, dc.port)
            _uiState.value = _uiState.value.copy(pingMs = rtt, isPinging = false)
            if (rtt >= 0) {
                log(LogEntry.Level.INFO, "tg_network", "Ping to ${dc.endpoint}: $rtt ms")
            } else {
                log(LogEntry.Level.WARN, "tg_network", "Ping to ${dc.endpoint} timed out.")
            }
        }
    }

    fun sendPingPacket() {
        viewModelScope.launch {
            val dummyPing = byteArrayOf(0x70, 0x69, 0x6E, 0x67) // "ping"
            val res = engine.sendFrame(dummyPing, _uiState.value.selectedProtocol)
            if (res.isSuccess) {
                log(LogEntry.Level.INFO, "tg_network", "Sent ping frame (4 bytes) over MTProto transport.")
            } else {
                log(LogEntry.Level.ERROR, "tg_network", "Failed to send frame: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun sendCustomMessage(text: String) {
        sendMessageToChat(_uiState.value.selectedChatId, text)
    }

    fun sendMessageToChat(chatId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val bytes = text.toByteArray(Charsets.UTF_8)
            engine.sendFrame(bytes, _uiState.value.selectedProtocol)

            val msg = MessageDto(
                id = System.currentTimeMillis(),
                chatId = chatId,
                senderId = 1001L, // Self
                text = text,
                timestamp = System.currentTimeMillis(),
                isOutgoing = true,
                deliveryStatus = 1
            )
            repository.saveMessage(msg)
            refreshStorage()
            log(LogEntry.Level.INFO, "tg_storage", "Сообщение сохранено в SQLite (чат #$chatId): \"$text\"")
        }
    }

    fun selectChat(chatId: Long) {
        _uiState.value = _uiState.value.copy(selectedChatId = chatId)
        viewModelScope.launch {
            val msgs = repository.getMessages(chatId)
            _uiState.value = _uiState.value.copy(selectedChatMessages = msgs)
        }
    }

    fun runPanicTest(scenario: String) {
        log(LogEntry.Level.WARN, "tg_ffi_panic", "Executing panic test scenario '$scenario' across FFI boundary...")
        val result = engine.testPanicBoundary(scenario)
        _uiState.value = _uiState.value.copy(
            panicResults = listOf(result) + _uiState.value.panicResults.take(19)
        )
        log(
            LogEntry.Level.PANIC_CAUGHT,
            "tg_ffi_panic",
            "JVM SURVIVED! Caught panic in [${result.context}]: ${result.errorReason}"
        )
    }

    fun generateNewKey() {
        val newSession = engine.generateNewAuthKey()
        _uiState.value = _uiState.value.copy(activeKeySession = newSession)
        log(LogEntry.Level.INFO, "tg_crypto", "Generated new 2048-bit AuthKey: ${newSession.keyId}")
    }

    fun zeroizeKey() {
        val sanitized = engine.zeroizeActiveKey()
        _uiState.value = _uiState.value.copy(activeKeySession = sanitized)
        log(LogEntry.Level.WARN, "tg_crypto", "ZeroizeOnDrop triggered! 256 bytes of sensitive key material zeroed in memory.")
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val chats = repository.getChats()
            val currentChatId = _uiState.value.selectedChatId
            val msgs = repository.getMessages(currentChatId)
            _uiState.value = _uiState.value.copy(
                chats = chats,
                selectedChatMessages = msgs
            )
        }
    }

    fun clearAllStorage() {
        viewModelScope.launch {
            repository.clearAllData()
            refreshStorage()
            log(LogEntry.Level.INFO, "tg_storage", "Таблицы SQLite chats, messages, users очищены и пересозданы.")
        }
    }

    fun runStorageBenchmark() {
        viewModelScope.launch {
            log(LogEntry.Level.INFO, "tg_storage", "Запуск бенчмарка 100 пакетных транзакций SQLite...")
            val result = repository.runBenchmark(100)
            _uiState.value = _uiState.value.copy(benchmarkResult = result)
            refreshStorage()
            log(
                LogEntry.Level.INFO,
                "tg_storage",
                "Бенчмарк завершен: 100 записей за ${result.durationMs} ms (${"%.1f".format(result.tps)} TPS)"
            )
        }
    }

    fun selectRustFile(path: String) {
        _uiState.value = _uiState.value.copy(selectedRustFile = path)
    }

    fun log(level: LogEntry.Level, tag: String, message: String) {
        val entry = LogEntry(level = level, tag = tag, message = message)
        _uiState.value = _uiState.value.copy(
            logs = listOf(entry) + _uiState.value.logs.take(99)
        )
    }

    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        engine.disconnect()
    }
}
