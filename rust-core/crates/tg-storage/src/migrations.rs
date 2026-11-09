//! Database schema definitions and migrations for local message & session cache.

use rusqlite::{Connection, Result};

pub const SCHEMA_VERSION: i32 = 1;

pub fn apply_migrations(conn: &Connection) -> Result<()> {
    conn.execute_batch(
        r#"
        PRAGMA journal_mode = WAL;
        PRAGMA synchronous = NORMAL;
        PRAGMA foreign_keys = ON;
        PRAGMA temp_store = MEMORY;
        PRAGMA cache_size = -4000; -- 4MB page cache

        CREATE TABLE IF NOT EXISTS schema_version (
            version INTEGER PRIMARY KEY
        );

        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY,
            peer_id INTEGER NOT NULL,
            sender_id INTEGER NOT NULL,
            body TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            delivery_status INTEGER DEFAULT 0 -- 0: Sending, 1: Sent, 2: Read
        );

        CREATE INDEX IF NOT EXISTS idx_messages_peer_ts 
            ON messages(peer_id, timestamp DESC);

        CREATE TABLE IF NOT EXISTS sessions (
            dc_id INTEGER PRIMARY KEY,
            server_address TEXT NOT NULL,
            port INTEGER NOT NULL,
            auth_key_id INTEGER NOT NULL,
            created_at INTEGER NOT NULL
        );
        "#,
    )?;

    conn.execute(
        "INSERT OR REPLACE INTO schema_version (version) VALUES (?1)",
        [SCHEMA_VERSION],
    )?;

    Ok(())
}
