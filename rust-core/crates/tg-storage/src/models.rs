//! Модели данных для локального хранилища: пользователи, чаты и сообщения.

use serde::{Deserialize, Serialize};

/// Запись пользователя Telegram
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct UserRecord {
    pub id: i64,
    pub first_name: String,
    pub last_name: Option<String>,
    pub username: Option<String>,
    pub phone: Option<String>,
    pub is_bot: bool,
}

/// Запись чата или диалога (личный, группа, канал)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ChatRecord {
    pub id: i64,
    pub title: String,
    pub chat_type: String, // "user", "chat", "channel"
    pub last_message_text: Option<String>,
    pub last_message_ts: i64,
    pub unread_count: i32,
}

/// Запись текстового сообщения
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MessageRecord {
    pub id: i64,
    pub chat_id: i64,
    pub sender_id: i64,
    pub text: String,
    pub timestamp: i64,
    pub is_outgoing: bool,
    pub delivery_status: i32, // 0: отправляется, 1: доставлено на сервер, 2: прочитано
}
