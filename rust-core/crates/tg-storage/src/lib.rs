pub mod migrations;

use parking_lot::Mutex;
use rusqlite::{params, Connection, Result as SqlResult};
use serde::{Deserialize, Serialize};
use std::path::Path;
use std::sync::Arc;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CachedMessage {
    pub id: i64,
    pub peer_id: i64,
    pub sender_id: i64,
    pub body: String,
    pub timestamp: i64,
    pub delivery_status: i32,
}

pub struct LocalStore {
    conn: Arc<Mutex<Connection>>,
}

impl LocalStore {
    pub fn open<P: AsRef<Path>>(path: P) -> SqlResult<Self> {
        let conn = Connection::open(path)?;
        migrations::apply_migrations(&conn)?;
        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    pub fn open_in_memory() -> SqlResult<Self> {
        let conn = Connection::open_in_memory()?;
        migrations::apply_migrations(&conn)?;
        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    pub fn insert_message(&self, msg: &CachedMessage) -> SqlResult<()> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO messages (id, peer_id, sender_id, body, timestamp, delivery_status) 
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                msg.id,
                msg.peer_id,
                msg.sender_id,
                msg.body,
                msg.timestamp,
                msg.delivery_status
            ],
        )?;
        Ok(())
    }

    pub fn get_messages_for_peer(&self, peer_id: i64, limit: usize) -> SqlResult<Vec<CachedMessage>> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT id, peer_id, sender_id, body, timestamp, delivery_status 
             FROM messages WHERE peer_id = ?1 ORDER BY timestamp DESC LIMIT ?2",
        )?;

        let rows = stmt.query_map(params![peer_id, limit as i64], |row| {
            Ok(CachedMessage {
                id: row.get(0)?,
                peer_id: row.get(1)?,
                sender_id: row.get(2)?,
                body: row.get(3)?,
                timestamp: row.get(4)?,
                delivery_status: row.get(5)?,
            })
        })?;

        let mut list = Vec::new();
        for r in rows {
            list.push(r?);
        }
        Ok(list)
    }

    pub fn count_messages(&self) -> SqlResult<i64> {
        let conn = self.conn.lock();
        conn.query_row("SELECT COUNT(*) FROM messages", [], |row| row.get(0))
    }
}
