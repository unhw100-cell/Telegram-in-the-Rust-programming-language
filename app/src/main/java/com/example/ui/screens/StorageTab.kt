package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.CachedMessage
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun StorageTab(
    uiState: UiState,
    onSearchChange: (String) -> Unit,
    onClearMessages: () -> Unit,
    onRunBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("storage_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: SQLite Engine Configuration & WAL Mode
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SQLITE CACHE ENGINE (RUSQLITE)",
                            color = TgCyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TgGreenSuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "WAL JOURNAL ACTIVE",
                                color = TgGreenSuccess,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Configured with PRAGMA synchronous = NORMAL, temp_store = MEMORY, and a 4MB page cache. " +
                                "Provides high-throughput non-blocking reads during background message ingestion.",
                        color = TgTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRunBenchmark,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TgCyanPrimary, contentColor = Color.Black),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("benchmark_button")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Batch Benchmark (100)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onClearMessages,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TgRedError),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TgRedError.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section 2: Benchmark Results Card (if run)
        uiState.benchmarkResult?.let { bench ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = TgDarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TgCyanPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("INSERTS", color = TgTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${bench.count}", color = TgTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Divider(modifier = Modifier.height(28.dp).width(1.dp), color = TgBorder)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DURATION", color = TgTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${bench.durationMs} ms", color = TgCyanPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Divider(modifier = Modifier.height(28.dp).width(1.dp), color = TgBorder)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("THROUGHPUT", color = TgTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${"%.0f".format(bench.tps)} TPS", color = TgGreenSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Section 3: Messages Search & List Header
        item {
            OutlinedTextField(
                value = uiState.messageSearchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search cached messages...", color = TgTextMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TgTextMuted, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_messages_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TgDarkSurface,
                    unfocusedContainerColor = TgDarkSurface,
                    focusedBorderColor = TgCyanPrimary,
                    unfocusedBorderColor = TgBorder,
                    focusedTextColor = TgTextPrimary,
                    unfocusedTextColor = TgTextPrimary
                ),
                singleLine = true
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CACHED MESSAGES",
                    color = TgCyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${uiState.cachedMessages.size} in local store",
                    color = TgTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (uiState.cachedMessages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TgDarkSurface)
                        .border(1.dp, TgBorder, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No messages in cache. Send a message or run the benchmark.", color = TgTextMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(uiState.cachedMessages) { msg ->
                MessageCard(msg)
            }
        }
    }
}

@Composable
fun MessageCard(msg: CachedMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (msg.senderId == 1001L) "You" else "Peer #${msg.senderId}",
                        color = if (msg.senderId == 1001L) TgCyanPrimary else TgBlueSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Chat ${msg.peerId}",
                        color = TgTextMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(msg.timestamp),
                    color = TgTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = msg.body,
                color = TgTextPrimary,
                fontSize = 13.sp
            )
        }
    }
}
