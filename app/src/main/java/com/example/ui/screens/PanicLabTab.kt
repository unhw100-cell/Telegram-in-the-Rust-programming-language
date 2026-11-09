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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.PanicTestResult
import com.example.ui.UiState
import com.example.ui.theme.*

@Composable
fun PanicLabTab(
    uiState: UiState,
    onRunPanicTest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("panic_lab_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner: Panic Invariant & Architecture Guarantee
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(TgRustAccent.copy(alpha = 0.6f), TgCyanPrimary.copy(alpha = 0.3f)))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TgRustAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = TgRustAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FFI PANIC SAFETY BARRIER",
                                color = TgRustAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "std::panic::catch_unwind + AssertUnwindSafe",
                                color = TgTextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "In Rust-Android NDK development, an unhandled panic crossing the extern \"C\" boundary instantly terminates the Android process via SIGABRT (fatal JVM crash). " +
                                "The safe_ffi! macro encapsulates every Kotlin-callable boundary in a catch_unwind closure, transforming catastrophic unwinds into graceful CoreError.InternalPanic exceptions.",
                        color = TgTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TgCodeBackground)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "safe_ffi!(\"action\", { /* Rust Code */ }) ➔ Result<T, CoreError>",
                            color = TgCyanPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Section 2: Interactive Panic Injection Triggers
        item {
            Text(
                text = "TRIGGER TEST PANIC SCENARIOS",
                color = TgCyanPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        item {
            val scenarios = listOf(
                Triple("oob", "Vector Index Out-of-Bounds", "Executes `vec[999]` on length 3. Tests slice bounds check failure."),
                Triple("unwrap", "Unwrap on None Option", "Calls `Option::unwrap()` on `None`. Tests null reference panic handling."),
                Triple("corrupt_frame", "Assertion Failure on CRC32", "Triggers `assert_eq!(crc, expected)`. Tests protocol frame desync."),
                Triple("stack_overflow_sim", "Worker Thread Crash", "Forces `panic!` inside a Tokio worker thread. Tests thread boundary survival.")
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                scenarios.forEach { (key, title, desc) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TgBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = TgTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    color = TgTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { onRunPanicTest(key) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TgRustAccent.copy(alpha = 0.2f),
                                    contentColor = TgRustAccent
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TgRustAccent.copy(alpha = 0.5f)),
                                modifier = Modifier.testTag("panic_btn_$key")
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("FIRE PANIC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Live Verification Log
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FFI SURVIVAL AUDIT LOG",
                    color = TgCyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${uiState.panicResults.size} audits",
                    color = TgTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (uiState.panicResults.isEmpty()) {
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
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = TgGreenSuccess,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "JVM Memory Space Protected",
                            color = TgGreenSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Tap any panic trigger above to test catch_unwind boundary",
                            color = TgTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(uiState.panicResults) { res ->
                PanicResultCard(res)
            }
        }
    }
}

@Composable
fun PanicResultCard(result: PanicTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, TgGreenSuccess.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TgGreenSuccess.copy(alpha = 0.15f))
                            .border(1.dp, TgGreenSuccess.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "JVM SURVIVED • ZERO CRASH",
                            color = TgGreenSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${result.executionTimeMs} ms unwind",
                        color = TgTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(result.timestamp),
                    color = TgTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Context: ${result.context}",
                color = TgCyanPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(TgCodeBackground)
                    .padding(10.dp)
            ) {
                Text(
                    text = result.errorReason,
                    color = TgRustAccent,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "✓ Panic safely unwound and caught in safe_ffi_boundary without propagating SIGABRT to Android OS.",
                color = TgTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
