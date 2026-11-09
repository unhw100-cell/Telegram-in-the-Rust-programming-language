//! Схема локальной реляционной базы данных SQLite.
//! Оптимизирована под мобильные накопители Android (UFS/eMMC):
//! - WAL journal mode для параллельного чтения без блокировки записи
//! - synchronous = NORMAL для баланса долговечности и производительности
//! - memory temp_store и 4MB page cache

use rusqlite::{Connection, Result};

pub fn apply_migrations(conn: &Connection) -> Result<()> {
    conn.execute_batch(
        r#"
        PRAGMA journal_mode = WAL;
        PRAGMA synchronous = NORMAL;
        PRAGMA foreign_keys = ON;
        PRAGMA temp_store = MEMORY;
        PRAGMA cache_size = -4000;

        -- Таблица пользователей Telegram
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY,
            first_name TEXT NOT NULL,
            last_name TEXT,
            username TEXT,
            phone TEXT,
            is_bot INTEGER NOT NULL DEFAULT 0
        );

        -- Таблица чатов (диалогов, групп, каналов)
        CREATE TABLE IF NOT EXISTS chats (
            id INTEGER PRIMARY KEY,
            title TEXT NOT NULL,
            chat_type TEXT NOT NULL DEFAULT 'user',
            last_message_text TEXT,
            last_message_ts INTEGER NOT NULL DEFAULT 0,
            unread_count INTEGER NOT NULL DEFAULT 0
        );

        -- Таблица сообщений
        CREATE TABLE IF NOT EXISTS messages (
            id INTEGER PRIMARY KEY,
            chat_id INTEGER NOT NULL,
            sender_id INTEGER NOT NULL,
            text TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            is_outgoing INTEGER NOT NULL DEFAULT 0,
            delivery_status INTEGER NOT NULL DEFAULT 1,
            FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
        );

        -- Быстрые индексы для выборки последних сообщений в чатах
        CREATE INDEX IF NOT EXISTS idx_messages_chat_ts 
            ON messages(chat_id, timestamp DESC);

        CREATE INDEX IF NOT EXISTS idx_chats_order 
            ON chats(last_message_ts DESC);
        "#,
    )?;

    Ok(())
}
