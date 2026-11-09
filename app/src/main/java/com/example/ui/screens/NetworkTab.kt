package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.core.DataCenter
import com.example.core.PacketRecord
import com.example.core.TELEGRAM_DCS
import com.example.core.TransportProtocol
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun NetworkTab(
    uiState: UiState,
    onSelectDc: (DataCenter) -> Unit,
    onSelectProtocol: (TransportProtocol) -> Unit,
    onSetCustomDc: (String, String) -> Unit,
    onSendPing: () -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var messageInput by remember { mutableStateOf("") }
    var showCustomDialog by remember { mutableStateOf(false) }
    var tempIp by remember { mutableStateOf(uiState.customIp) }
    var tempPort by remember { mutableStateOf(uiState.customPort) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("network_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Data Center Selector
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TELEGRAM DATA CENTERS",
                        color = TgCyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Custom Endpoint",
                        color = TgBlueSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { showCustomDialog = true }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(TELEGRAM_DCS) { dc ->
                        val isSelected = uiState.selectedDc.id == dc.id && !uiState.isCustomDc
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable { onSelectDc(dc) }
                                .testTag("dc_card_${dc.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) TgDarkSurfaceContainer else TgDarkSurface
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, TgCyanPrimary)
                            else androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DC ${dc.id}",
                                        color = if (isSelected) TgCyanPrimary else TgTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = TgCyanPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = dc.name.substringAfter(" "),
                                    color = TgTextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${dc.ip}:${dc.port}",
                                    color = TgTextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dc.location,
                                    color = TgBlueSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Transport Protocol Framing
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "MTPROTO TRANSPORT PROTOCOL",
                        color = TgCyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TransportProtocol.values().forEach { proto ->
                            val isSelected = uiState.selectedProtocol == proto
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) TgCyanPrimary.copy(alpha = 0.15f) else TgDarkSurfaceVariant)
                                    .border(
                                        1.dp,
                                        if (isSelected) TgCyanPrimary else TgBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectProtocol(proto) }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = proto.label,
                                        color = if (isSelected) TgCyanPrimary else TgTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = proto.markerHex,
                                        color = TgTextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.selectedProtocol.description,
                        color = TgTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Section 3: Interactive Packet Dispatcher
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PACKET DISPATCHER & INJECTION",
                        color = TgCyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSendPing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TgDarkSurfaceVariant, contentColor = TgCyanPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Ping Frame", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Enter message payload...", fontSize = 12.sp, color = TgTextMuted) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input_field"),
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
                                    onSendMessage(messageInput)
                                    messageInput = ""
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TgCyanPrimary, contentColor = Color.Black),
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("send_message_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Section 4: Live Packet Stream Inspector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE MTPROTO PACKET STREAM",
                    color = TgCyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${uiState.packets.size} frames logged",
                    color = TgTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (uiState.packets.isEmpty()) {
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FilterFrames,
                            contentDescription = null,
                            tint = TgTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No MTProto frames recorded yet",
                            color = TgTextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Connect to Telegram DC or dispatch a frame above",
                            color = TgTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(uiState.packets) { packet ->
                PacketItemCard(packet)
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Configure Custom DC Endpoint", color = TgTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter IP address and port of your Telegram proxy or custom DC:", color = TgTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = tempIp,
                        onValueChange = { tempIp = it },
                        label = { Text("IP Address") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TgTextPrimary,
                            unfocusedTextColor = TgTextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = tempPort,
                        onValueChange = { tempPort = it },
                        label = { Text("Port (usually 443)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TgTextPrimary,
                            unfocusedTextColor = TgTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetCustomDc(tempIp, tempPort)
                        showCustomDialog = false
                    }
                ) {
                    Text("Apply Endpoint", color = TgCyanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Cancel", color = TgTextMuted)
                }
            },
            containerColor = TgDarkSurface
        )
    }
}

@Composable
fun PacketItemCard(packet: PacketRecord) {
    val isOutbound = packet.direction == PacketRecord.Direction.OUTBOUND
    val dirColor = if (isOutbound) TgBlueSecondary else TgGreenSuccess
    val dirLabel = if (isOutbound) "TX ➔ OUT" else "RX ⬅ IN"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, dirColor.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dirLabel,
                        color = dirColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(dirColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${packet.sizeBytes} bytes",
                        color = TgTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(packet.timestampMs),
                    color = TgTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = packet.description,
                color = TgTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TgCodeBackground)
                    .padding(8.dp)
            ) {
                Text(
                    text = packet.hexPreview,
                    color = TgCyanPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
