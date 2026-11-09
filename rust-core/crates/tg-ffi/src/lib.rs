mod error;
mod panic_hook;
mod core;

pub use error::CoreError;
pub use panic_hook::safe_ffi_boundary;
pub use core::{CoreEventListener, TelegramCore};

uniffi::setup_scaffolding!();
