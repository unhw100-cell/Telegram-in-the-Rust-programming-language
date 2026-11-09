package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun CryptoVaultTab(
    uiState: UiState,
    onGenerateKey: () -> Unit,
    onZeroizeKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = uiState.activeKeySession

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("crypto_vault_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner: MTProto 2.0 AuthKey & Zeroize Security
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(TgCyanPrimary.copy(alpha = 0.6f), TgBlueSecondary.copy(alpha = 0.3f)))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TgCyanPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = TgCyanPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MTPROTO 2.0 AUTH KEY VAULT",
                                color = TgCyanPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Zeroize & ZeroizeOnDrop Memory Guard",
                                color = TgTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Telegram MTProto 2.0 authorization keys contain 2048 bits (256 bytes) of sensitive entropy. " +
                                "Standard heap deallocation leaves key remnants in RAM that can be recovered via cold boot or root memory dump attacks. " +
                                "Rust's ZeroizeOnDrop trait guarantees that raw memory is immediately overwritten with zeros (0x00) whenever the key is dropped or rotated.",
                        color = TgTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Active Session Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (session.isZeroized) TgAmberWarning.copy(alpha = 0.5f) else TgGreenSuccess.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE SESSION AUTH_KEY",
                            color = TgCyanPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        val statusLabel = if (session.isZeroized) "ZEROIZED / PURGED" else "ACTIVE ENCRYPTED"
                        val statusColor = if (session.isZeroized) TgAmberWarning else TgGreenSuccess
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "auth_key_id (Lower 64-bit SHA-256):",
                        color = TgTextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = session.keyId,
                        color = TgTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Raw Memory Buffer (256 bytes):",
                        color = TgTextMuted,
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TgCodeBackground)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = session.rawKeyHex,
                            color = if (session.isZeroized) TgAmberWarning else TgCyanPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onGenerateKey,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TgDarkSurfaceVariant, contentColor = TgCyanPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generate_key_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New 2048-bit Key", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onZeroizeKey,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TgRustAccent.copy(alpha = 0.2f),
                                contentColor = TgRustAccent
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TgRustAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("zeroize_key_button")
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trigger Zeroize", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Crypto Primitives Breakdown
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CRYPTOGRAPHIC SPECIFICATIONS",
                        color = TgCyanPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val specs = listOf(
                        Pair("Payload Cipher", "AES-256 IGE (Infinite Garble Extension)"),
                        Pair("Message Key Hash", "SHA-256 (MTProto 2.0 specification)"),
                        Pair("Key Exchange", "Diffie-Hellman 2048-bit prime moduli"),
                        Pair("Memory Sanitation", "zeroize = { version = \"1.8\", features = [\"derive\"] }"),
                        Pair("Session Expiry", "Active rotation every 100 messages or 24h")
                    )

                    specs.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = TgTextSecondary, fontSize = 12.sp)
                            Text(value, color = TgTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
