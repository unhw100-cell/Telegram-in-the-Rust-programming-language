pub mod migrations;
pub mod models;

pub use models::{ChatRecord, MessageRecord, UserRecord};

use parking_lot::Mutex;
use rusqlite::{params, Connection, Result as SqlResult};
use std::path::Path;
use std::sync::Arc;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum StorageError {
    #[error("Ошибка базы данных SQLite: {0}")]
    Sqlite(#[from] rusqlite::Error),

    #[error("Сущность не найдена: {0}")]
    NotFound(String),
}

/// Потокобезопасная обертка базы данных SQLite для мобильного приложения
#[derive(Clone)]
pub struct Storage {
    conn: Arc<Mutex<Connection>>,
}

impl Storage {
    /// Открытие базы данных по файловому пути с автоматическим применением миграций
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self, StorageError> {
        let conn = Connection::open(path)?;
        migrations::apply_migrations(&conn)?;

        // Заполняем начальными системными чатами, если база только создана
        let mut count: i64 = 0;
        conn.query_row("SELECT COUNT(*) FROM chats", [], |r| {
            count = r.get(0)?;
            Ok(())
        })?;

        if count == 0 {
            Self::seed_initial_data(&conn)?;
        }

        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    /// Открытие in-memory базы для тестов
    pub fn open_in_memory() -> Result<Self, StorageError> {
        let conn = Connection::open_in_memory()?;
        migrations::apply_migrations(&conn)?;
        Self::seed_initial_data(&conn)?;
        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
        })
    }

    fn seed_initial_data(conn: &Connection) -> Result<(), rusqlite::Error> {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as i64)
            .unwrap_or(0);

        conn.execute(
            "INSERT OR REPLACE INTO users (id, first_name, last_name, username, phone, is_bot) 
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![777, "Telegram", "Notifications", "telegram", "42777", 0],
        )?;

        conn.execute(
            "INSERT OR REPLACE INTO chats (id, title, chat_type, last_message_text, last_message_ts, unread_count)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![777, "Telegram Notifications", "channel", "Добро пожаловать в Telegram Rust Core!", now, 1],
        )?;

        conn.execute(
            "INSERT OR REPLACE INTO messages (id, chat_id, sender_id, text, timestamp, is_outgoing, delivery_status)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![now, 777, 777, "Добро пожаловать в Telegram Rust Core! Локальное SQLite хранилище инициализировано.", now, 0, 2],
        )?;

        Ok(())
    }

    // --- ОПЕРАЦИИ С ЧАТАМИ ---

    pub fn get_chats(&self) -> Result<Vec<ChatRecord>, StorageError> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT id, title, chat_type, last_message_text, last_message_ts, unread_count 
             FROM chats ORDER BY last_message_ts DESC",
        )?;

        let rows = stmt.query_map([], |row| {
            Ok(ChatRecord {
                id: row.get(0)?,
                title: row.get(1)?,
                chat_type: row.get(2)?,
                last_message_text: row.get(3)?,
                last_message_ts: row.get(4)?,
                unread_count: row.get(5)?,
            })
        })?;

        let mut chats = Vec::new();
        for r in rows {
            chats.push(r?);
        }
        Ok(chats)
    }

    pub fn save_chat(&self, chat: &ChatRecord) -> Result<(), StorageError> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT INTO chats (id, title, chat_type, last_message_text, last_message_ts, unread_count)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)
             ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                last_message_text = COALESCE(excluded.last_message_text, chats.last_message_text),
                last_message_ts = MAX(excluded.last_message_ts, chats.last_message_ts),
                unread_count = excluded.unread_count",
            params![
                chat.id,
                chat.title,
                chat.chat_type,
                chat.last_message_text,
                chat.last_message_ts,
                chat.unread_count
            ],
        )?;
        Ok(())
    }

    pub fn get_chat(&self, chat_id: i64) -> Result<Option<ChatRecord>, StorageError> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT id, title, chat_type, last_message_text, last_message_ts, unread_count 
             FROM chats WHERE id = ?1",
        )?;

        let mut rows = stmt.query(params![chat_id])?;
        if let Some(row) = rows.next()? {
            Ok(Some(ChatRecord {
                id: row.get(0)?,
                title: row.get(1)?,
                chat_type: row.get(2)?,
                last_message_text: row.get(3)?,
                last_message_ts: row.get(4)?,
                unread_count: row.get(5)?,
            }))
        } else {
            Ok(None)
        }
    }

    // --- ОПЕРАЦИИ С СООБЩЕНИЯМИ ---

    pub fn get_messages(&self, chat_id: i64, limit: usize, offset: usize) -> Result<Vec<MessageRecord>, StorageError> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT id, chat_id, sender_id, text, timestamp, is_outgoing, delivery_status 
             FROM messages WHERE chat_id = ?1 
             ORDER BY timestamp DESC LIMIT ?2 OFFSET ?3",
        )?;

        let rows = stmt.query_map(params![chat_id, limit as i64, offset as i64], |row| {
            let is_outgoing_int: i32 = row.get(5)?;
            Ok(MessageRecord {
                id: row.get(0)?,
                chat_id: row.get(1)?,
                sender_id: row.get(2)?,
                text: row.get(3)?,
                timestamp: row.get(4)?,
                is_outgoing: is_outgoing_int != 0,
                delivery_status: row.get(6)?,
            })
        })?;

        let mut messages = Vec::new();
        for r in rows {
            messages.push(r?);
        }
        // Возвращаем в хронологическом порядке (старые -> новые)
        messages.reverse();
        Ok(messages)
    }

    pub fn save_message(&self, msg: &MessageRecord) -> Result<(), StorageError> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO messages (id, chat_id, sender_id, text, timestamp, is_outgoing, delivery_status)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                msg.id,
                msg.chat_id,
                msg.sender_id,
                msg.text,
                msg.timestamp,
                if msg.is_outgoing { 1 } else { 0 },
                msg.delivery_status
            ],
        )?;

        // Обновляем последнее сообщение в чате
        conn.execute(
            "UPDATE chats SET 
                last_message_text = ?1,
                last_message_ts = ?2,
                unread_count = unread_count + (CASE WHEN ?3 = 0 THEN 1 ELSE 0 END)
             WHERE id = ?4",
            params![msg.text, msg.timestamp, if msg.is_outgoing { 1 } else { 0 }, msg.chat_id],
        )?;

        Ok(())
    }

    // --- ОПЕРАЦИИ С ПОЛЬЗОВАТЕЛЯМИ ---

    pub fn save_user(&self, user: &UserRecord) -> Result<(), StorageError> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT OR REPLACE INTO users (id, first_name, last_name, username, phone, is_bot)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                user.id,
                user.first_name,
                user.last_name,
                user.username,
                user.phone,
                if user.is_bot { 1 } else { 0 }
            ],
        )?;
        Ok(())
    }

    pub fn get_user(&self, user_id: i64) -> Result<Option<UserRecord>, StorageError> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT id, first_name, last_name, username, phone, is_bot FROM users WHERE id = ?1",
        )?;

        let mut rows = stmt.query(params![user_id])?;
        if let Some(row) = rows.next()? {
            let is_bot_int: i32 = row.get(5)?;
            Ok(Some(UserRecord {
                id: row.get(0)?,
                first_name: row.get(1)?,
                last_name: row.get(2)?,
                username: row.get(3)?,
                phone: row.get(4)?,
                is_bot: is_bot_int != 0,
            }))
        } else {
            Ok(None)
        }
    }
}
