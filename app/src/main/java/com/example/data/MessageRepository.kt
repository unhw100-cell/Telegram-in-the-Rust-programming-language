package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.core.ChatDto
import com.example.core.MessageDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android SQLite implementation strictly matching the Rust tg-storage crate.
 * Configured with WAL journal mode, synchronous=NORMAL, and 4MB page cache.
 */
class MessageRepository(context: Context) : SQLiteOpenHelper(context, "tg_database.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        // Таблица пользователей
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY,
                first_name TEXT NOT NULL,
                last_name TEXT,
                username TEXT,
                phone TEXT,
                is_bot INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        // Таблица чатов
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chats (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                chat_type TEXT NOT NULL DEFAULT 'user',
                last_message_text TEXT,
                last_message_ts INTEGER DEFAULT 0,
                unread_count INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        // Таблица сообщений
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY,
                chat_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                is_outgoing INTEGER DEFAULT 0,
                delivery_status INTEGER DEFAULT 1,
                FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_chat_ts ON messages(chat_id, timestamp DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chats_order ON chats(last_message_ts DESC)")

        // Начальные данные (Seed data)
        seedInitialData(db)
    }

    private fun seedInitialData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()

        // 1. Системный чат Telegram Notifications
        val chat1 = ContentValues().apply {
            put("id", 777L)
            put("title", "Telegram Notifications")
            put("chat_type", "channel")
            put("last_message_text", "MTProto 2.0 сессия успешно создана с DC 2.")
            put("last_message_ts", now - 600000)
            put("unread_count", 1)
        }
        db.insertWithOnConflict("chats", null, chat1, SQLiteDatabase.CONFLICT_REPLACE)

        // 2. Групповой чат Rust Core Architecture
        val chat2 = ContentValues().apply {
            put("id", 1002L)
            put("title", "Rust Core Architect Team")
            put("chat_type", "group")
            put("last_message_text", "Tokio multi-thread runtime инициализирован (2 воркера).")
            put("last_message_ts", now - 1800000)
            put("unread_count", 0)
        }
        db.insertWithOnConflict("chats", null, chat2, SQLiteDatabase.CONFLICT_REPLACE)

        // 3. Личный чат с Pavel Durov
        val chat3 = ContentValues().apply {
            put("id", 1003L)
            put("title", "Pavel Durov")
            put("chat_type", "user")
            put("last_message_text", "Безопасность памяти с Zeroize работает отлично.")
            put("last_message_ts", now - 3600000)
            put("unread_count", 0)
        }
        db.insertWithOnConflict("chats", null, chat3, SQLiteDatabase.CONFLICT_REPLACE)

        // Начальные сообщения
        val msg1 = ContentValues().apply {
            put("id", now - 600000)
            put("chat_id", 777L)
            put("sender_id", 777L)
            put("text", "MTProto 2.0 сессия успешно создана с DC 2.")
            put("timestamp", now - 600000)
            put("is_outgoing", 0)
            put("delivery_status", 2)
        }
        val msg2 = ContentValues().apply {
            put("id", now - 1800000)
            put("chat_id", 1002L)
            put("sender_id", 1001L) // Self
            put("text", "Tokio multi-thread runtime инициализирован (2 воркера).")
            put("timestamp", now - 1800000)
            put("is_outgoing", 1)
            put("delivery_status", 2)
        }
        val msg3 = ContentValues().apply {
            put("id", now - 3600000)
            put("chat_id", 1003L)
            put("sender_id", 1003L)
            put("text", "Безопасность памяти с Zeroize работает отлично.")
            put("timestamp", now - 3600000)
            put("is_outgoing", 0)
            put("delivery_status", 2)
        }

        db.insertWithOnConflict("messages", null, msg1, SQLiteDatabase.CONFLICT_REPLACE)
        db.insertWithOnConflict("messages", null, msg2, SQLiteDatabase.CONFLICT_REPLACE)
        db.insertWithOnConflict("messages", null, msg3, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("PRAGMA temp_store = MEMORY")
        db.execSQL("PRAGMA cache_size = -4000") // 4MB page cache
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS chats")
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    // --- ОПЕРАЦИИ С ЧАТАМИ ---

    suspend fun getChats(): List<ChatDto> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<ChatDto>()
        val cursor = db.query(
            "chats",
            arrayOf("id", "title", "chat_type", "last_message_text", "last_message_ts", "unread_count"),
            null,
            null,
            null,
            null,
            "last_message_ts DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    ChatDto(
                        id = c.getLong(0),
                        title = c.getString(1),
                        chatType = c.getString(2),
                        lastMessageText = c.getString(3),
                        lastMessageTs = c.getLong(4),
                        unreadCount = c.getInt(5)
                    )
                )
            }
        }
        list
    }

    suspend fun saveChat(chat: ChatDto): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", chat.id)
            put("title", chat.title)
            put("chat_type", chat.chatType)
            put("last_message_text", chat.lastMessageText)
            put("last_message_ts", chat.lastMessageTs)
            put("unread_count", chat.unreadCount)
        }
        db.insertWithOnConflict("chats", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // --- ОПЕРАЦИИ С СООБЩЕНИЯМИ ---

    suspend fun getMessages(chatId: Long, limit: Int = 50, offset: Int = 0): List<MessageDto> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<MessageDto>()
        val cursor = db.query(
            "messages",
            arrayOf("id", "chat_id", "sender_id", "text", "timestamp", "is_outgoing", "delivery_status"),
            "chat_id = ?",
            arrayOf(chatId.toString()),
            null,
            null,
            "timestamp ASC",
            "$offset, $limit"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    MessageDto(
                        id = c.getLong(0),
                        chatId = c.getLong(1),
                        senderId = c.getLong(2),
                        text = c.getString(3),
                        timestamp = c.getLong(4),
                        isOutgoing = c.getInt(5) == 1,
                        deliveryStatus = c.getInt(6)
                    )
                )
            }
        }
        list
    }

    suspend fun saveMessage(msg: MessageDto): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", msg.id)
            put("chat_id", msg.chatId)
            put("sender_id", msg.senderId)
            put("text", msg.text)
            put("timestamp", msg.timestamp)
            put("is_outgoing", if (msg.isOutgoing) 1 else 0)
            put("delivery_status", msg.deliveryStatus)
        }
        db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        // Обновляем последнее сообщение в чате
        val chatCv = ContentValues().apply {
            put("last_message_text", msg.text)
            put("last_message_ts", msg.timestamp)
        }
        db.update("chats", chatCv, "id = ?", arrayOf(msg.chatId.toString()))
    }

    suspend fun clearAllData(): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete("messages", null, null)
        db.delete("chats", null, null)
        seedInitialData(db)
    }

    /**
     * Бенчмарк пакетной вставки 100 сообщений
     */
    suspend fun runBenchmark(count: Int = 100): BenchmarkResult = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val start = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            for (i in 0 until count) {
                val cv = ContentValues().apply {
                    put("id", now + i)
                    put("chat_id", 1002L)
                    put("sender_id", 1001L)
                    put("text", "Пакетная транзакция SQLite #$i - проверка PRAGMA WAL режима")
                    put("timestamp", now + i)
                    put("is_outgoing", 1)
                    put("delivery_status", 1)
                }
                db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val durationMs = (System.currentTimeMillis() - start).coerceAtLeast(1)
        val tps = (count * 1000.0) / durationMs
        BenchmarkResult(count, durationMs, tps)
    }

    data class BenchmarkResult(val count: Int, val durationMs: Long, val tps: Double)
}
