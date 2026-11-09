# Telegram Rust Core (High-Performance MTProto Mobile Engine)

Production-grade Telegram client engine written in Rust and bridged to Android via UniFFI with zero-cost panic safety.

## Architecture

- **`tg-crypto`**: MTProto 2.0 AuthKey generation (2048-bit), SHA-256 msg_key hashing, and `ZeroizeOnDrop` memory sanitation.
- **`tg-network`**: Tokio async runtime with Abridged / Intermediate TCP framing and low-latency socket buffers.
- **`tg-storage`**: SQLite message & session caching with WAL journal mode and modern SQLite optimizations.
- **`tg-ffi`**: UniFFI export definitions, `safe_ffi!` panic catcher boundary preventing JVM crashes, and 2-worker Tokio thread pool.

## Build Instructions (Android NDK)

1. Install `cargo-ndk` and target architectures:
```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android
rustup target add armv7-linux-androideabi
rustup target add x86_64-linux-android
```

2. Compile for Android ARM64 (`aarch64-linux-android`):
```bash
cargo ndk -t aarch64-linux-android -o ../app/src/main/jniLibs build --release -p tg-ffi
```

3. Generate Kotlin bindings:
```bash
cargo run --features=uniffi/cli --bin uniffi-bindgen generate \
    crates/tg-ffi/src/lib.rs \
    --language kotlin \
    --out-dir ../app/src/main/java/com/example/core
```
