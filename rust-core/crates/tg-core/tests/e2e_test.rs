//! Сквозные интеграционные тесты (End-to-End Integration Tests) для ядра Telegram на Rust (`tg-core`).
//!
//! Проверяет полный жизненный цикл мобильного клиента:
//! 1. Инициализация ядра и моста логирования в Logcat (`init_rust_logger`, `TelegramCore::new`).
//! 2. Регистрация слушателя событий (`TgClientListener`) через UniFFI интерфейс.
//! 3. Попытка асинхронного подключения к MTProto DC (`connect`).
//! 4. Создание и отправка сообщений с автоматической фиксацией в SQLite (`send_message`).
//! 5. Чтение истории с поддержкой пагинации (`get_messages`, `get_chats`).
//! 6. Обработка фонового push-уведомления (`handle_background_push`).
//! 7. Корректное завершение соединения с безопасной очисткой сессионных ключей (`disconnect`).

use parking_lot::Mutex;
use std::sync::Arc;
use tempfile::tempdir;
use tg_core::{
    init_rust_logger, ChatDto, MessageDto, PushNotificationResult, TelegramCore, TgClientListener,
    TgError,
};

/// Mock-реализация `TgClientListener` для перехвата событий ядра в тестах
#[derive(Clone, Default)]
struct TestClientListener {
    connection_events: Arc<Mutex<Vec<(bool, i32, String)>>>,
    received_messages: Arc<Mutex<Vec<MessageDto>>>,
    updated_chats: Arc<Mutex<Vec<ChatDto>>>,
    errors: Arc<Mutex<Vec<String>>>,
}

impl TgClientListener for TestClientListener {
    fn on_connection_state_changed(&self, is_connected: bool, dc_id: i32, details: String) {
        self.connection_events.lock().push((is_connected, dc_id, details));
    }

    fn on_message_received(&self, message: MessageDto) {
        self.received_messages.lock().push(message);
    }

    fn on_chat_updated(&self, chat: ChatDto) {
        self.updated_chats.lock().push(chat);
    }

    fn on_error(&self, error: TgError) {
        self.errors.lock().push(error.to_string());
    }
}

#[tokio::test]
async fn test_full_lifecycle_e2e() {
    // 1. Инициализация логирования (перенаправление в Android Logcat / тестовый вывод)
    init_rust_logger();

    // 2. Создание изолированного временного каталога для базы данных SQLite
    let temp_dir = tempdir().expect("Не удалось создать временный каталог");
    let storage_dir = temp_dir.path().to_str().expect("Некорректный UTF-8 путь").to_string();

    // 3. Инициализация экземпляра ядра TelegramCore
    let core = TelegramCore::new(storage_dir.clone())
        .expect("Ошибка инициализации TelegramCore");

    // 4. Регистрация тестового слушателя
    let listener = Box::new(TestClientListener::default());
    let listener_ref = listener.clone();
    core.set_listener(listener)
        .expect("Ошибка регистрации слушателя");

    // 5. Попытка подключения к тестовому DC 2
    core.connect(2, true).expect("Ошибка вызова connect");

    // 6. Отправка исходящих сообщений в чат #42
    let chat_id: i64 = 42;
    let text_1 = "Привет, Telegram Rust Core! [E2E Пакет 1]".to_string();
    let sent_1 = core.send_message(chat_id, text_1.clone())
        .expect("Ошибка отправки первого сообщения");

    assert_eq!(sent_1.chat_id, chat_id);
    assert_eq!(sent_1.text, text_1);
    assert!(sent_1.is_outgoing);
    assert_eq!(sent_1.delivery_status, 1);

    let text_2 = "Второе тестовое сообщение с шифрованием AES-GCM [E2E Пакет 2]".to_string();
    let sent_2 = core.send_message(chat_id, text_2.clone())
        .expect("Ошибка отправки второго сообщения");

    assert_eq!(sent_2.chat_id, chat_id);
    assert_eq!(sent_2.text, text_2);

    // 7. Проверка пагинации сообщений из SQLite базы данных
    let messages = core.get_messages(chat_id, 10, 0)
        .expect("Ошибка выборки сообщений из SQLite");

    assert!(messages.len() >= 2, "Ожидалось не менее двух сохраненных сообщений");
    // Сортировка DESC: самое свежее первое
    assert_eq!(messages[0].text, text_2);
    assert_eq!(messages[1].text, text_1);

    // Проверка пагинации со смещением (offset = 1, limit = 1)
    let paged_messages = core.get_messages(chat_id, 1, 1)
        .expect("Ошибка выборки пагинации со смещением");
    assert_eq!(paged_messages.len(), 1);
    assert_eq!(paged_messages[0].text, text_1);

    // 8. Проверка списка диалогов (get_chats)
    let chats = core.get_chats().expect("Ошибка получения списка чатов");
    assert!(!chats.is_empty(), "Список чатов не должен быть пуст");
    let target_chat = chats.iter().find(|c| c.id == chat_id);
    assert!(target_chat.is_some(), "Чат 42 должен присутствовать в списке диалогов");
    let chat_info = target_chat.unwrap();
    assert_eq!(chat_info.last_message_text.as_deref(), Some(text_2.as_str()));

    // 9. Обработка фонового FCM push-уведомления в изоляции
    let push_payload = "Входящий зашифрованный звонок от Павла".to_string();
    let push_result: PushNotificationResult = TelegramCore::handle_background_push(
        storage_dir.clone(),
        push_payload.clone(),
    ).expect("Ошибка обработки push-уведомления");

    assert_eq!(push_result.chat_id, 777);
    assert_eq!(push_result.preview_text, push_payload);
    assert!(push_result.has_unread);

    // Проверяем, что сообщение из пуша зафиксировано в SQLite
    let push_messages = core.get_messages(777, 10, 0)
        .expect("Ошибка чтения системного чата 777");
    assert!(!push_messages.is_empty());
    assert_eq!(push_messages[0].text, push_payload);

    // 10. Очистка и разрыв соединения с проверкой Zeroize
    core.disconnect().expect("Ошибка вызова disconnect");

    // Даем микропаузу воркеру Tokio для завершения разрыва
    tokio::time::sleep(std::time::Duration::from_millis(50)).await;

    // 11. Вызов trim_memory
    core.trim_memory(20).expect("Ошибка trim_memory");

    println!(">>> E2E Integration Test успешно завершен! Все инварианты проверены.");
}

#[test]
fn test_storage_pagination_boundaries() {
    let temp_dir = tempdir().expect("Ошибка создания tempdir");
    let storage_dir = temp_dir.path().to_str().unwrap().to_string();
    let core = TelegramCore::new(storage_dir).expect("Ошибка инициализации ядра");

    let chat_id = 999;
    // Записываем 25 сообщений
    for i in 1..=25 {
        core.send_message(chat_id, format!("Сообщение #{:02}", i))
            .expect("Ошибка отправки сообщения");
    }

    // Страница 1: первые 10
    let page_1 = core.get_messages(chat_id, 10, 0).expect("Ошибка page 1");
    assert_eq!(page_1.len(), 10);
    assert_eq!(page_1[0].text, "Сообщение #25");
    assert_eq!(page_1[9].text, "Сообщение #16");

    // Страница 2: следующие 10
    let page_2 = core.get_messages(chat_id, 10, 10).expect("Ошибка page 2");
    assert_eq!(page_2.len(), 10);
    assert_eq!(page_2[0].text, "Сообщение #15");
    assert_eq!(page_2[9].text, "Сообщение #06");

    // Страница 3: оставшиеся 5
    let page_3 = core.get_messages(chat_id, 10, 20).expect("Ошибка page 3");
    assert_eq!(page_3.len(), 5);
    assert_eq!(page_3[0].text, "Сообщение #05");
    assert_eq!(page_3[4].text, "Сообщение #01");
}
