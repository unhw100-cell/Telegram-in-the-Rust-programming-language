package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ConnectionState
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun HeaderStatusCard(
    uiState: UiState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onPingClick: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = uiState.connectionState is ConnectionState.Connected
    val isConnecting = uiState.connectionState is ConnectionState.Connecting ||
            uiState.connectionState is ConnectionState.Handshaking

    // Pulsating animation for active connection
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("header_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = TgDarkSurface
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    if (isConnected) TgCyanPrimary.copy(alpha = 0.6f) else TgBorder,
                    if (isConnected) TgBlueSecondary.copy(alpha = 0.3f) else TgBorder
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Row 1: Brand & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(TgCyanPrimary, TgBlueSecondary))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "TG Logo",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "TG RUST CORE",
                                color = TgTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "v0.1.0",
                                color = TgCyanPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TgCyanPrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "UniFFI • Tokio 2-Worker • MTProto 2.0",
                            color = TgTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status pill
                val (statusColor, statusText) = when (uiState.connectionState) {
                    is ConnectionState.Connected -> Pair(TgGreenSuccess, "CONNECTED")
                    is ConnectionState.Connecting -> Pair(TgAmberWarning, "CONNECTING")
                    is ConnectionState.Handshaking -> Pair(TgCyanPrimary, "HANDSHAKE")
                    is ConnectionState.Error -> Pair(TgRedError, "ERROR")
                    is ConnectionState.Disconnected -> Pair(TgTextMuted, "OFFLINE")
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected || isConnecting) statusColor.copy(alpha = pulseAlpha)
                                else statusColor
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2: Target DC & Metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TgDarkSurfaceVariant)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TARGET ENDPOINT",
                        color = TgTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${uiState.selectedDc.name} (${uiState.selectedDc.endpoint})",
                        color = TgTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${uiState.selectedProtocol.label} • ${uiState.selectedDc.location}",
                        color = TgTextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Latency pill
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "RTT PING",
                        color = TgTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val rttDisplay = when {
                        uiState.connectionState is ConnectionState.Connected -> "${(uiState.connectionState as ConnectionState.Connected).latencyMs} ms"
                        uiState.pingMs >= 0 -> "${uiState.pingMs} ms"
                        uiState.isPinging -> "..."
                        else -> "--"
                    }
                    Text(
                        text = rttDisplay,
                        color = if (uiState.pingMs in 0..150 || (uiState.connectionState is ConnectionState.Connected)) TgGreenSuccess else TgAmberWarning,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isConnected) {
                    Button(
                        onClick = onConnectClick,
                        enabled = !isConnecting,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("connect_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TgCyanPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Handshaking...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CONNECT MTPROTO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onDisconnectClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("disconnect_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TgRedError.copy(alpha = 0.2f),
                            contentColor = TgRedError
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TgRedError.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DISCONNECT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Ping button
                OutlinedButton(
                    onClick = onPingClick,
                    enabled = !uiState.isPinging,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("ping_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TgBlueSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TgBlueSecondary.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PING", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Logs button
                IconButton(
                    onClick = onOpenLogs,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TgDarkSurfaceVariant)
                        .testTag("logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Logs",
                        tint = TgTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
