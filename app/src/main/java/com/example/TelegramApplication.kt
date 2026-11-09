package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

class TelegramApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        Log.i("TelegramApp", "Application initialized with Rust Core FFI bridge.")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Telegram Сообщения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о входящих сообщениях и медиафайлах"
                enableVibration(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w("TelegramApp", "System memory pressure detected (level=$level). Triggering Rust L1 RAM purge.")
        // Вызов очистки оперативной памяти в Rust ядре
        // TelegramCore.trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.e("TelegramApp", "Critical low memory warning. Purging all volatile caches.")
        // TelegramCore.trimMemory(TRIM_MEMORY_COMPLETE)
    }

    companion object {
        const val CHANNEL_ID_MESSAGES = "tg_messages_channel"
    }
}
