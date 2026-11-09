package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.core.ChatDto
import com.example.core.MessageDto
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun StorageTab(
    uiState: UiState,
    onSelectChat: (Long) -> Unit,
    onSendMessage: (Long, String) -> Unit,
    onClearAll: () -> Unit,
    onRunBenchmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageInput by remember { mutableStateOf("") }
    val selectedChat = uiState.chats.find { it.id == uiState.selectedChatId } ?: uiState.chats.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("storage_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: SQLite Storage Engine Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SQLITE STORAGE (TG-STORAGE)",
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Таблицы 'chats', 'messages', 'users' синхронизированы в SQLite. " +
                                "Все входящие пакеты из Tokio Event Loop автоматически сохраняются в БД без блокировки UI.",
                        color = TgTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            Text("Бенчмарк (100 вписей)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onClearAll,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TgRedError),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TgRedError.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сброс", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Section 2: Benchmark Results (if any)
        uiState.benchmarkResult?.let { bench ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = TgDarkSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TgCyanPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ТРАНЗАКЦИИ", color = TgTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${bench.count}", color = TgTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp), color = TgBorder)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ВРЕМЯ", color = TgTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${bench.durationMs} ms", color = TgCyanPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Divider(modifier = Modifier.height(24.dp).width(1.dp), color = TgBorder)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("СКОРОСТЬ", color = TgTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("${"%.0f".format(bench.tps)} TPS", color = TgGreenSuccess, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Section 3: Chats Carousel (get_chats FFI)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ДИАЛОГИ И ЧАТЫ (GET_CHATS)",
                        color = TgCyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${uiState.chats.size} чатов в SQLite",
                        color = TgTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.chats) { chat ->
                        val isSelected = chat.id == uiState.selectedChatId
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) TgDarkSurfaceContainer else TgDarkSurface)
                                .border(
                                    1.5.dp,
                                    if (isSelected) TgCyanPrimary else TgBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectChat(chat.id) }
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = chat.title,
                                        color = if (isSelected) TgCyanPrimary else TgTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                    if (chat.unreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(TgCyanPrimary)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${chat.unreadCount}",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = chat.lastMessageText ?: "Нет сообщений",
                                    color = TgTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = chat.chatType.uppercase(),
                                    color = TgBlueSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Chat Message Thread (get_messages FFI)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = TgCyanPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedChat?.title ?: "Чат",
                                color = TgTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "get_messages(${uiState.selectedChatId})",
                            color = TgTextMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Message list inside selected chat
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TgCodeBackground)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.selectedChatMessages.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("История сообщений пуста", color = TgTextMuted, fontSize = 12.sp)
                            }
                        } else {
                            uiState.selectedChatMessages.forEach { msg ->
                                MessageBubble(msg)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Message input field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Написать в чат...", fontSize = 12.sp, color = TgTextMuted) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = TgDarkSurfaceVariant,
                                unfocusedContainerColor = TgDarkSurfaceVariant,
                                focusedBorderColor = TgCyanPrimary,
                                unfocusedBorderColor = TgBorder,
                                focusedTextColor = TgTextPrimary,
                                unfocusedTextColor = TgTextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    onSendMessage(uiState.selectedChatId, messageInput)
                                    messageInput = ""
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TgCyanPrimary, contentColor = Color.Black),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Отправить", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(msg: MessageDto) {
    val isOut = msg.isOutgoing
    val bubbleColor = if (isOut) TgDarkSurfaceContainer else TgDarkSurfaceVariant
    val align = if (isOut) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isOut) 12.dp else 2.dp,
                        bottomEnd = if (isOut) 2.dp else 12.dp
                    )
                )
                .background(bubbleColor)
                .border(1.dp, if (isOut) TgCyanPrimary.copy(alpha = 0.3f) else TgBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Column {
                Text(
                    text = msg.text,
                    color = TgTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(msg.timestamp),
                        color = TgTextMuted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (isOut) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.DoneAll,
                            contentDescription = "Delivered",
                            tint = TgCyanPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
