package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.core.CachedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android SQLite implementation matching the Rust tg-storage crate.
 * Configured with WAL journal mode, synchronous=NORMAL, and 4MB cache size.
 */
class MessageRepository(context: Context) : SQLiteOpenHelper(context, "tg_cache.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS messages (
                id INTEGER PRIMARY KEY,
                peer_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                body TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                delivery_status INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_peer ON messages(peer_id, timestamp DESC)")

        // Insert initial demo messages matching MTProto channels
        val now = System.currentTimeMillis()
        val initialMessages = listOf(
            CachedMessage(now - 3600000, 1001, 777, "Welcome to Telegram Rust Core engine console.", now - 3600000, 2),
            CachedMessage(now - 1800000, 1001, 777, "MTProto 2.0 handshake verified with DC 2 (Amsterdam).", now - 1800000, 2),
            CachedMessage(now - 600000, 1001, 1001, "Tokio multi-thread runtime initialized with 2 worker threads.", now - 600000, 1)
        )
        for (msg in initialMessages) {
            val cv = ContentValues().apply {
                put("id", msg.id)
                put("peer_id", msg.peerId)
                put("sender_id", msg.senderId)
                put("body", msg.body)
                put("timestamp", msg.timestamp)
                put("delivery_status", msg.deliveryStatus)
            }
            db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // High-performance SQLite pragmas matching tg-storage
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous = NORMAL")
        db.execSQL("PRAGMA temp_store = MEMORY")
        db.execSQL("PRAGMA cache_size = -4000") // 4MB cache
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        onCreate(db)
    }

    suspend fun insertMessage(msg: CachedMessage): Unit = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", msg.id)
            put("peer_id", msg.peerId)
            put("sender_id", msg.senderId)
            put("body", msg.body)
            put("timestamp", msg.timestamp)
            put("delivery_status", msg.deliveryStatus)
        }
        db.insertWithOnConflict("messages", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getMessages(peerId: Long? = null, query: String = "", limit: Int = 100): List<CachedMessage> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val list = mutableListOf<CachedMessage>()

        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (peerId != null) {
            whereClauses.add("peer_id = ?")
            args.add(peerId.toString())
        }
        if (query.isNotBlank()) {
            whereClauses.add("body LIKE ?")
            args.add("%$query%")
        }

        val whereStr = if (whereClauses.isNotEmpty()) whereClauses.joinToString(" AND ") else null
        val cursor = db.query(
            "messages",
            arrayOf("id", "peer_id", "sender_id", "body", "timestamp", "delivery_status"),
            whereStr,
            if (args.isNotEmpty()) args.toTypedArray() else null,
            null,
            null,
            "timestamp DESC",
            limit.toString()
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    CachedMessage(
                        id = c.getLong(0),
                        peerId = c.getLong(1),
                        senderId = c.getLong(2),
                        body = c.getString(3),
                        timestamp = c.getLong(4),
                        deliveryStatus = c.getInt(5)
                    )
                )
            }
        }
        list
    }

    suspend fun clearMessages(): Unit = withContext(Dispatchers.IO) {
        writableDatabase.delete("messages", null, null)
    }

    /**
     * Executes a batch insert benchmark of 100 records and measures TPS & total latency
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
                    put("peer_id", 1001)
                    put("sender_id", 777)
                    put("body", "Benchmark test record #$i - MTProto encrypted payload frame verification")
                    put("timestamp", now + i)
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
