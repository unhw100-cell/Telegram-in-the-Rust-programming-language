pub mod cipher;
pub mod migrations;
pub mod models;

pub use cipher::{CipherError, DatabaseCipher};
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

    #[error("Ошибка шифрования AES-256-GCM: {0}")]
    Cipher(#[from] cipher::CipherError),

    #[error("Сущность не найдена: {0}")]
    NotFound(String),
}

/// Потокобезопасная обертка базы данных SQLite с прозрачным AES-256-GCM шифрованием
#[derive(Clone)]
pub struct Storage {
    conn: Arc<Mutex<Connection>>,
    cipher: DatabaseCipher,
}

impl Storage {
    /// Открытие базы данных с ключом из Android Keystore (или дефолтным)
    pub fn open<P: AsRef<Path>>(path: P) -> Result<Self, StorageError> {
        let cipher = DatabaseCipher::default_keystore_derived();
        Self::open_with_cipher(path, cipher)
    }

    /// Открытие базы с явным шифратором
    pub fn open_with_cipher<P: AsRef<Path>>(path: P, cipher: DatabaseCipher) -> Result<Self, StorageError> {
        let conn = Connection::open(path)?;
        migrations::apply_migrations(&conn)?;

        let mut count: i64 = 0;
        conn.query_row("SELECT COUNT(*) FROM chats", [], |r| {
            count = r.get(0)?;
            Ok(())
        })?;

        if count == 0 {
            Self::seed_initial_data(&conn, &cipher)?;
        }

        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
            cipher,
        })
    }

    /// Открытие in-memory базы для тестов
    pub fn open_in_memory() -> Result<Self, StorageError> {
        let cipher = DatabaseCipher::default_keystore_derived();
        let conn = Connection::open_in_memory()?;
        migrations::apply_migrations(&conn)?;
        Self::seed_initial_data(&conn, &cipher)?;
        Ok(Self {
            conn: Arc::new(Mutex::new(conn)),
            cipher,
        })
    }

    fn seed_initial_data(conn: &Connection, cipher: &DatabaseCipher) -> Result<(), rusqlite::Error> {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as i64)
            .unwrap_or(0);

        conn.execute(
            "INSERT OR REPLACE INTO users (id, first_name, last_name, username, phone, is_bot) 
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![777, "Telegram", "Notifications", "telegram", "42777", 0],
        )?;

        let enc_preview = cipher.encrypt_text("Добро пожаловать в Telegram Rust Core! База зашифрована AES-256-GCM.")
            .unwrap_or_else(|_| "Добро пожаловать в Telegram Rust Core!".to_string());

        conn.execute(
            "INSERT OR REPLACE INTO chats (id, title, chat_type, last_message_text, last_message_ts, unread_count)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![777, "Telegram Notifications", "channel", enc_preview, now, 1],
        )?;

        let enc_msg = cipher.encrypt_text("Добро пожаловать в Telegram Rust Core! Локальное хранилище защищено AES-256-GCM.")
            .unwrap_or_else(|_| "Добро пожаловать!".to_string());

        conn.execute(
            "INSERT OR REPLACE INTO messages (id, chat_id, sender_id, text, timestamp, is_outgoing, delivery_status)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![now, 777, 777, enc_msg, now, 0, 2],
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
            let enc_last_text: Option<String> = row.get(3)?;
            let plain_last_text = enc_last_text.and_then(|t| self.cipher.decrypt_text(&t).ok());

            Ok(ChatRecord {
                id: row.get(0)?,
                title: row.get(1)?,
                chat_type: row.get(2)?,
                last_message_text: plain_last_text,
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

    pub fn get_chat(&self, chat_id: i64) -> Result<Option<ChatRecord>, StorageError> {
        let conn = self.conn.lock();
        let mut stmt = conn.prepare(
            "SELECT id, title, chat_type, last_message_text, last_message_ts, unread_count 
             FROM chats WHERE id = ?1",
        )?;

        let mut rows = stmt.query(params![chat_id])?;
        if let Some(row) = rows.next()? {
            let enc_last_text: Option<String> = row.get(3)?;
            let plain_last_text = enc_last_text.and_then(|t| self.cipher.decrypt_text(&t).ok());

            Ok(Some(ChatRecord {
                id: row.get(0)?,
                title: row.get(1)?,
                chat_type: row.get(2)?,
                last_message_text: plain_last_text,
                last_message_ts: row.get(4)?,
                unread_count: row.get(5)?,
            }))
        } else {
            Ok(None)
        }
    }

    pub fn save_chat(&self, chat: &ChatRecord) -> Result<(), StorageError> {
        let conn = self.conn.lock();

        let enc_last_text = match &chat.last_message_text {
            Some(t) => Some(self.cipher.encrypt_text(t)?),
            None => None,
        };

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
                enc_last_text,
                chat.last_message_ts,
                chat.unread_count
            ],
        )?;
        Ok(())
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
            let encrypted_text: String = row.get(3)?;
            // Прозрачная дешифрация текста сообщения
            let plain_text = self.cipher.decrypt_text(&encrypted_text)
                .unwrap_or_else(|_| "[Ошибка расшифровки / Поврежденные данные]".to_string());

            Ok(MessageRecord {
                id: row.get(0)?,
                chat_id: row.get(1)?,
                sender_id: row.get(2)?,
                text: plain_text,
                timestamp: row.get(4)?,
                is_outgoing: is_outgoing_int != 0,
                delivery_status: row.get(6)?,
            })
        })?;

        let mut messages = Vec::new();
        for r in rows {
            messages.push(r?);
        }
        messages.reverse();
        Ok(messages)
    }

    pub fn save_message(&self, msg: &MessageRecord) -> Result<(), StorageError> {
        let conn = self.conn.lock();

        // 1. Шифруем текст сообщения перед сохранением в SQLite
        let encrypted_text = self.cipher.encrypt_text(&msg.text)?;

        conn.execute(
            "INSERT OR REPLACE INTO messages (id, chat_id, sender_id, text, timestamp, is_outgoing, delivery_status)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            params![
                msg.id,
                msg.chat_id,
                msg.sender_id,
                encrypted_text,
                msg.timestamp,
                if msg.is_outgoing { 1 } else { 0 },
                msg.delivery_status
            ],
        )?;

        // Обновляем последнее сообщение чата (также в зашифрованном виде)
        conn.execute(
            "UPDATE chats SET 
                last_message_text = ?1,
                last_message_ts = ?2,
                unread_count = unread_count + (CASE WHEN ?3 = 0 THEN 1 ELSE 0 END)
             WHERE id = ?4",
            params![encrypted_text, msg.timestamp, if msg.is_outgoing { 1 } else { 0 }, msg.chat_id],
        )?;

        Ok(())
    }

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
