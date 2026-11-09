//! Panic hook and safe FFI execution boundary.
//! Prevents Rust panics from propagating into JVM / causing process termination.

use crate::error::CoreError;
use std::panic::{catch_unwind, AssertUnwindSafe};

/// Executes any closure inside catch_unwind and maps any caught panic to CoreError::InternalPanic.
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

/// Macro to wrap any FFI-accessible call in safe boundary
#[macro_export]
macro_rules! safe_ffi {
    ($name:expr, $body:expr) => {
        $crate::panic_hook::safe_ffi_boundary($name, || $body)
    };
}
