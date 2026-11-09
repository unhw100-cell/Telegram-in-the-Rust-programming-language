//! TelegramCore main engine with Tokio runtime and UniFFI exports.

use crate::error::CoreError;
use crate::safe_ffi;
use parking_lot::RwLock;
use std::sync::Arc;
use std::time::Duration;
use tg_crypto::{generate_ephemeral_auth_key, AuthKey};
use tg_network::{Connection, TransportProtocol};
use tg_storage::{CachedMessage, LocalStore};
use tokio::runtime::{Builder, Runtime};
use tokio::sync::mpsc;

/// Callback interface implemented by Kotlin ViewModel
#[uniffi::export(callback_interface)]
pub trait CoreEventListener: Send + Sync + 'static {
    fn on_connection_state_changed(&self, is_connected: bool, details: String);
    fn on_new_message(&self, message_id: i64, text: String);
    fn on_packet_transmitted(&self, direction: String, byte_count: u32, preview_hex: String);
    fn on_error(&self, error: CoreError);
}

#[derive(Default)]
struct CoreState {
    is_connected: bool,
    active_dc_id: i32,
    endpoint: String,
    auth_key: Option<AuthKey>,
}

#[derive(uniffi::Object)]
pub struct TelegramCore {
    runtime: Arc<Runtime>,
    state: Arc<RwLock<CoreState>>,
    storage: Arc<LocalStore>,
    outbound_tx: mpsc::Sender<Vec<u8>>,
    listener: Arc<RwLock<Option<Box<dyn CoreEventListener>>>>,
}

#[uniffi::export]
impl TelegramCore {
    /// Factory constructor initializing the Tokio runtime (2 threads, optimized for mobile ARM64)
    #[uniffi::constructor]
    pub fn new(storage_dir: String) -> Result<Arc<Self>, CoreError> {
        safe_ffi!("TelegramCore::new", {
            let _ = tracing_android::try_init("TelegramCore");

            let runtime = Builder::new_multi_thread()
                .worker_threads(2)
                .thread_name("tg-core-runtime")
                .enable_all()
                .build()
                .map_err(|e| CoreError::IllegalState { reason: e.to_string() })?;

            let db_path = std::path::Path::new(&storage_dir).join("tg_cache.db");
            let storage = LocalStore::open(db_path)?;

            let (outbound_tx, _outbound_rx) = mpsc::channel::<Vec<u8>>(128);

            let core = Arc::new(Self {
                runtime: Arc::new(runtime),
                state: Arc::new(RwLock::new(CoreState::default())),
                storage: Arc::new(storage),
                outbound_tx,
                listener: Arc::new(RwLock::new(None)),
            });

            Ok(core)
        })
    }

    /// Sets the Kotlin UI/ViewModel event listener
    pub fn set_event_listener(&self, listener: Box<dyn CoreEventListener>) -> Result<(), CoreError> {
        safe_ffi!("TelegramCore::set_event_listener", {
            let mut guard = self.listener.write();
            *guard = Some(listener);
            Ok(())
        })
    }

    /// Connects to a Telegram DC (e.g., "149.154.167.50:443")
    pub fn connect(&self, endpoint: String) -> Result<(), CoreError> {
        safe_ffi!("TelegramCore::connect", {
            let state = Arc::clone(&self.state);
            let listener_store = Arc::clone(&self.listener);
            let storage = Arc::clone(&self.storage);
            let target_endpoint = endpoint.clone();

            self.runtime.spawn(async move {
                let mut conn = Connection::new(TransportProtocol::Abridged);
                tracing::info!(target: "tg_net", "Connecting to MTProto endpoint: {}", target_endpoint);

                match conn.connect(&target_endpoint, Duration::from_secs(5)).await {
                    Ok(_) => {
                        let auth_key = generate_ephemeral_auth_key();
                        let key_id = auth_key.key_id();
                        {
                            let mut s = state.write();
                            s.is_connected = true;
                            s.endpoint = target_endpoint.clone();
                            s.auth_key = Some(auth_key);
                        }

                        if let Some(listener) = listener_store.read().as_ref() {
                            listener.on_connection_state_changed(
                                true,
                                format!("Connected to {} [KeyID: {:x}]", target_endpoint, key_id),
                            );
                        }

                        loop {
                            match conn.read_frame().await {
                                Ok(frame) => {
                                    let preview = frame.iter().take(8).map(|b| format!("{:02x}", b)).collect::<Vec<_>>().join(" ");
                                    if let Some(listener) = listener_store.read().as_ref() {
                                        listener.on_packet_transmitted("INBOUND".to_string(), frame.len() as u32, preview);
                                    }

                                    let dummy_id = std::time::SystemTime::now()
                                        .duration_since(std::time::UNIX_EPOCH)
                                        .unwrap()
                                        .as_millis() as i64;
                                    let body = format!("MTProto Frame len={} bytes", frame.len());
                                    let msg = CachedMessage {
                                        id: dummy_id,
                                        peer_id: 1001,
                                        sender_id: 777,
                                        body: body.clone(),
                                        timestamp: dummy_id,
                                        delivery_status: 1,
                                    };
                                    let _ = storage.insert_message(&msg);

                                    if let Some(listener) = listener_store.read().as_ref() {
                                        listener.on_new_message(dummy_id, body);
                                    }
                                }
                                Err(err) => {
                                    tracing::error!(target: "tg_net", "Connection drop: {:?}", err);
                                    let mut s = state.write();
                                    s.is_connected = false;
                                    if let Some(listener) = listener_store.read().as_ref() {
                                        listener.on_connection_state_changed(false, format!("Disconnected: {:?}", err));
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    Err(e) => {
                        tracing::error!(target: "tg_net", "Failed to connect: {:?}", e);
                        if let Some(listener) = listener_store.read().as_ref() {
                            listener.on_error(CoreError::Network { reason: e.to_string() });
                        }
                    }
                }
            });

            Ok(())
        })
    }

    /// Transmits raw packet payload through the outbound channel
    pub fn send_packet(&self, payload: Vec<u8>) -> Result<(), CoreError> {
        safe_ffi!("TelegramCore::send_packet", {
            let tx = self.outbound_tx.clone();
            self.runtime.spawn(async move {
                let _ = tx.send(payload).await;
            });
            Ok(())
        })
    }

    /// Explicit panic test to verify safe FFI boundary behavior from Kotlin
    pub fn test_panic_boundary(&self, scenario: String) -> Result<String, CoreError> {
        safe_ffi!("TelegramCore::test_panic_boundary", {
            match scenario.as_str() {
                "oob" => {
                    let v = vec![1, 2, 3];
                    #[allow(unconditional_panic)]
                    let _val = v[999]; // Deliberate panic
                    Ok("Should not reach".to_string())
                }
                "unwrap" => {
                    let opt: Option<i32> = None;
                    let _val = opt.unwrap(); // Deliberate panic
                    Ok("Should not reach".to_string())
                }
                _ => {
                    panic!("Deliberate assertion failure in Rust core: {}", scenario);
                }
            }
        })
    }
}
