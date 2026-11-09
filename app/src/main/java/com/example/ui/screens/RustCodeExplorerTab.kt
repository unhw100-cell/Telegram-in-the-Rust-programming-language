package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.UiState
import com.example.ui.theme.*

data class RustSourceFile(
    val path: String,
    val name: String,
    val crateName: String,
    val code: String
)

val RUST_FILES = listOf(
    RustSourceFile(
        path = "crates/tg-network/src/connection.rs",
        name = "connection.rs",
        crateName = "tg-network",
        code = """
// Асинхронное подключение к серверам Telegram через Tokio TCP
use bytes::{Buf, BufMut, BytesMut};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio::time::timeout;

pub struct TgConnection {
    stream: Option<TcpStream>,
    transport: TransportKind,
    read_buffer: BytesMut,
    write_buffer: BytesMut,
}

impl TgConnection {
    pub async fn connect_dc(&mut self, dc_id: i32, is_test: bool, timeout_dur: Duration) -> Result<(), NetworkError> {
        let ep = DataCenterEndpoint::lookup(dc_id, is_test)?;
        let tcp_stream = timeout(timeout_dur, TcpStream::connect(ep.to_socket_addr()?)).await??;
        tcp_stream.set_nodelay(true)?; // Минимизация задержки пакетов

        let mut stream = tcp_stream;
        stream.write_all(&[0xef]).await?; // Стартовый байт MTProto Abridged
        stream.flush().await?;

        self.stream = Some(stream);
        Ok(())
    }
}
""".trimIndent()
    ),
    RustSourceFile(
        path = "crates/tg-core/src/lib.rs",
        name = "lib.rs",
        crateName = "tg-core",
        code = """
// Точка входа ядра TelegramCore с безопасным UniFFI для Kotlin
uniffi::setup_scaffolding!();

#[derive(uniffi::Object)]
pub struct TelegramCore {
    runtime: Arc<Runtime>,
    connection: Arc<TokioMutex<TgConnection>>,
    session: Arc<RwLock<Option<Session>>>,
    listener: Arc<RwLock<Option<Box<dyn TgClientListener>>>>,
}

#[uniffi::export]
impl TelegramCore {
    #[uniffi::constructor]
    pub fn new(storage_dir: String) -> Result<Arc<Self>, TgError> {
        safe_ffi!("TelegramCore::new", {
            let runtime = Builder::new_multi_thread().worker_threads(2).build()?;
            Ok(Arc::new(Self { ... }))
        })
    }

    pub fn connect(&self, dc_id: i32, is_test: bool) -> Result<(), TgError> {
        safe_ffi!("TelegramCore::connect", {
            self.runtime.spawn(async move {
                // Подключение по TCP и создание Zeroize-сессии
            });
            Ok(())
        })
    }
}
""".trimIndent()
    ),
    RustSourceFile(
        path = "crates/tg-ffi/src/panic_hook.rs",
        name = "panic_hook.rs",
        crateName = "tg-ffi",
        code = """
// Strict Panic Safety Barrier: prevents SIGABRT across FFI boundary
use crate::error::CoreError;
use std::panic::{catch_unwind, AssertUnwindSafe};

pub fn safe_ffi_boundary<F, R>(action_name: &'static str, f: F) -> Result<R, CoreError>
where
    F: FnOnce() -> Result<R, CoreError>,
{
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(result) => result,
        Err(panic_payload) => {
            let reason = if let Some(s) = panic_payload.downcast_ref::<&str>() {
                s.to_string()
            } else if let Some(s) = panic_payload.downcast_ref::<String>() {
                s.clone()
            } else {
                "Unknown panic payload".to_string()
            };

            tracing::error!(
                target: "tg_ffi_panic",
                action = action_name,
                reason = %reason,
                "CRITICAL: Caught panic on Rust FFI boundary"
            );

            Err(CoreError::InternalPanic {
                context: action_name.to_string(),
                reason,
            })
        }
    }
}

#[macro_export]
macro_rules! safe_ffi {
    (${'$'}name:expr, ${'$'}body:expr) => {
        ${'$'}crate::panic_hook::safe_ffi_boundary(${'$'}name, || ${'$'}body)
    };
}
""".trimIndent()
    ),
    RustSourceFile(
        path = "crates/tg-crypto/src/lib.rs",
        name = "lib.rs",
        crateName = "tg-crypto",
        code = """
// 2048-bit AuthKey with ZeroizeOnDrop memory sanitization
use byteorder::{ByteOrder, LittleEndian};
use sha2::{Digest, Sha256};
use zeroize::{Zeroize, ZeroizeOnDrop};

#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct AuthKey {
    key: [u8; 256],
    auth_key_id: u64,
}

impl AuthKey {
    pub fn new(raw_key: [u8; 256]) -> Self {
        let mut hasher = Sha256::new();
        hasher.update(&raw_key);
        let hash = hasher.finalize();
        let auth_key_id = LittleEndian::read_u64(&hash[hash.len() - 8..]);
        Self { key: raw_key, auth_key_id }
    }

    pub fn key_id(&self) -> u64 {
        self.auth_key_id
    }

    pub fn raw_slice(&self) -> &[u8] {
        &self.key
    }
}
""".trimIndent()
    ),
    RustSourceFile(
        path = "crates/tg-network/src/codec.rs",
        name = "codec.rs",
        crateName = "tg-network",
        code = """
// MTProto Abridged & Intermediate framing codecs
use bytes::{Buf, BufMut, BytesMut};
use std::io;

pub enum TransportProtocol {
    Abridged,            // Initial byte 0xef
    Intermediate,        // Initial byte 0xeeeeeeee
    PaddedIntermediate,  // Initial byte 0xdddddddd
}

pub struct Codec {
    protocol: TransportProtocol,
}

impl Codec {
    pub fn encode_frame(&self, payload: &[u8], dst: &mut BytesMut) {
        match self.protocol {
            TransportProtocol::Abridged => {
                let word_len = payload.len() / 4;
                if word_len < 0x7f {
                    dst.put_u8(word_len as u8);
                } else {
                    dst.put_u8(0x7f);
                    dst.put_u8((word_len & 0xff) as u8);
                    dst.put_u8(((word_len >> 8) & 0xff) as u8);
                    dst.put_u8(((word_len >> 16) & 0xff) as u8);
                }
                dst.put_slice(payload);
            }
            _ => { /* Intermediate 4-byte prefix */ }
        }
    }
}
""".trimIndent()
    ),
    RustSourceFile(
        path = "crates/tg-storage/src/lib.rs",
        name = "lib.rs",
        crateName = "tg-storage",
        code = """
// SQLite high-performance cache with WAL journal mode
use parking_lot::Mutex;
use rusqlite::{params, Connection, Result as SqlResult};
use std::sync::Arc;

pub struct LocalStore {
    conn: Arc<Mutex<Connection>>,
}

impl LocalStore {
    pub fn open<P: AsRef<std::path::Path>>(path: P) -> SqlResult<Self> {
        let conn = Connection::open(path)?;
        conn.execute_batch(r#"
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;
            PRAGMA cache_size = -4000;
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY,
                peer_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                delivery_status INTEGER DEFAULT 0
            );
        "#)?;
        Ok(Self { conn: Arc::new(Mutex::new(conn)) })
    }
}
""".trimIndent()
    ),
    RustSourceFile(
        path = "Cargo.toml",
        name = "Cargo.toml",
        crateName = "root",
        code = """
[workspace]
resolver = "2"
members = [
    "crates/tg-crypto",
    "crates/tg-network",
    "crates/tg-storage",
    "crates/tg-ffi",
]

[profile.release]
opt-level = 3
lto = "fat"
codegen-units = 1
panic = "unwind" # Required for catch_unwind on FFI!
strip = "symbols"
""".trimIndent()
    )
)

@Composable
fun RustCodeExplorerTab(
    uiState: UiState,
    onSelectFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentFile = RUST_FILES.find { it.path == uiState.selectedRustFile } ?: RUST_FILES.first()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("rust_code_explorer_tab"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // File tabs
        item {
            Column {
                Text(
                    text = "RUST WORKSPACE MODULES",
                    color = TgCyanPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RUST_FILES) { file ->
                        val isSelected = file.path == currentFile.path
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) TgCyanPrimary.copy(alpha = 0.2f) else TgDarkSurface)
                                .border(1.dp, if (isSelected) TgCyanPrimary else TgBorder, RoundedCornerShape(10.dp))
                                .clickable { onSelectFile(file.path) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = file.name,
                                    color = if (isSelected) TgCyanPrimary else TgTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = file.crateName,
                                    color = TgTextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active File Viewer Card
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
                            Icon(Icons.Default.Code, contentDescription = null, tint = TgCyanPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentFile.path,
                                color = TgTextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Rust Source", currentFile.code)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied ${currentFile.name} to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = TgCyanPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(TgCodeBackground)
                            .horizontalScroll(scrollState)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentFile.code,
                            color = TgTextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Build command recipe card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TgDarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, TgRustAccent.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "CARGO NDK CROSS-COMPILATION RECIPE",
                        color = TgRustAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TgCodeBackground)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "cargo ndk -t aarch64-linux-android -o ../app/src/main/jniLibs build --release -p tg-ffi",
                            color = TgCyanPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
