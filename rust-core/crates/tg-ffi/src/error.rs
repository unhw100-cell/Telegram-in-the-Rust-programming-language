//! Typed error taxonomy for UniFFI FFI boundary.

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("Network I/O failed: {reason}")]
    Network { reason: String },

    #[error("Storage failure: {reason}")]
    Storage { reason: String },

    #[error("Cryptography error: {reason}")]
    Crypto { reason: String },

    #[error("Invalid state transition: {reason}")]
    IllegalState { reason: String },

    #[error("Critical Rust Panic in [{context}]: {reason}")]
    InternalPanic { context: String, reason: String },
}

impl From<std::io::Error> for CoreError {
    fn from(err: std::io::Error) -> Self {
        CoreError::Network { reason: err.to_string() }
    }
}

impl From<rusqlite::Error> for CoreError {
    fn from(err: rusqlite::Error) -> Self {
        CoreError::Storage { reason: err.to_string() }
    }
}
