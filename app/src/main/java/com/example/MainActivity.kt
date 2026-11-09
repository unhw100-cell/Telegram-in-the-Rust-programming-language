package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.TelegramViewModel
import com.example.ui.components.AppTab
import com.example.ui.components.HeaderStatusCard
import com.example.ui.components.LogsBottomSheet
import com.example.ui.components.MainNavigationBar
import com.example.ui.screens.*
import com.example.ui.theme.TgDarkBackground
import com.example.ui.theme.TelegramCoreTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TelegramViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TelegramCoreTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var currentTab by remember { mutableStateOf(AppTab.NETWORK) }
                var showLogsSheet by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TgDarkBackground)
                        .testTag("main_scaffold"),
                    bottomBar = {
                        MainNavigationBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    },
                    containerColor = TgDarkBackground,
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Persistent Header Status Card
                        HeaderStatusCard(
                            uiState = uiState,
                            onConnectClick = { viewModel.connect() },
                            onDisconnectClick = { viewModel.disconnect() },
                            onPingClick = { viewModel.pingTarget() },
                            onOpenLogs = { showLogsSheet = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // Screen content based on current tab
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                AppTab.NETWORK -> {
                                    NetworkTab(
                                        uiState = uiState,
                                        onSelectDc = { viewModel.selectDc(it) },
                                        onSelectProtocol = { viewModel.selectProtocol(it) },
                                        onSetCustomDc = { ip, port -> viewModel.setCustomDc(ip, port) },
                                        onSendPing = { viewModel.sendPingPacket() },
                                        onSendMessage = { viewModel.sendCustomMessage(it) }
                                    )
                                }
                                AppTab.PANIC_LAB -> {
                                    PanicLabTab(
                                        uiState = uiState,
                                        onRunPanicTest = { viewModel.runPanicTest(it) }
                                    )
                                }
                                AppTab.CRYPTO -> {
                                    CryptoVaultTab(
                                        uiState = uiState,
                                        onGenerateKey = { viewModel.generateNewKey() },
                                        onZeroizeKey = { viewModel.zeroizeKey() }
                                    )
                                }
                                AppTab.STORAGE -> {
                                    StorageTab(
                                        uiState = uiState,
                                        onSelectChat = { viewModel.selectChat(it) },
                                        onSendMessage = { chatId, text -> viewModel.sendMessageToChat(chatId, text) },
                                        onClearAll = { viewModel.clearAllStorage() },
                                        onRunBenchmark = { viewModel.runStorageBenchmark() }
                                    )
                                }
                                AppTab.RUST_CODE -> {
                                    RustCodeExplorerTab(
                                        uiState = uiState,
                                        onSelectFile = { viewModel.selectRustFile(it) }
                                    )
                                }
                            }
                        }
                    }

                    if (showLogsSheet) {
                        LogsBottomSheet(
                            logs = uiState.logs,
                            onClearLogs = { viewModel.clearLogs() },
                            onDismiss = { showLogsSheet = false }
                        )
                    }
                }
            }
        }
    }
}
