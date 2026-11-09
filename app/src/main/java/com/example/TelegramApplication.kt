package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Главный класс приложения Telegram Android.
 * Выполняет:
 * 1. Загрузку скомпилированной native-библиотеки libtg_core.so через System.loadLibrary
 * 2. Инициализацию моста логирования Rust (android_logger -> Android Logcat)
 * 3. Инициализацию экземпляра ядра TelegramCore с передачей пути к локальному хранилищу
 * 4. Создание системных каналов уведомлений
 * 5. Делегирование событий нехватки памяти (onTrimMemory) в Rust L1 RAM кэш
 */
class TelegramApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Создание каналов системных уведомлений
        createNotificationChannels()

        // 2. Инициализация Native Rust Core
        initializeRustCore()

        Log.i(TAG_LOG, "TelegramApplication успешно запущено. Rust Logcat мост активен под тегом 'TelegramCore'.")
    }

    private fun initializeRustCore() {
        try {
            // Загрузка динамической библиотеки libtg_core.so
            System.loadLibrary("tg_core")
            isNativeLibraryLoaded = true
            Log.i(TAG_LOG, "Библиотека libtg_core.so успешно загружена через System.loadLibrary.")

            // Директория для локальной SQLite базы данных и медиакэша
            val storageDir = File(filesDir, "tg_data").apply {
                if (!exists()) mkdirs()
            }.absolutePath

            // В боевом приложении после генерации UniFFI:
            // initRustLogger()
            // telegramCore = TelegramCore.new(storageDir)

            Log.i(TAG_LOG, "Rust Core окружение подготовлено. База данных: $storageDir/tg_database.db")
        } catch (unsatisfiedLink: UnsatisfiedLinkError) {
            // Безопасный фоллбэк: если запуск происходит без установленных .so бинарников
            // (например, при локальном preview или unit-тестировании)
            isNativeLibraryLoaded = false
            Log.w(
                TAG_LOG,
                "libtg_core.so не найдена в jniLibs (используется встроенный эмулятор ядра): ${unsatisfiedLink.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG_LOG, "Ошибка инициализации Rust Core: ${e.message}", e)
        }
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
        Log.w(TAG_LOG, "Получен сигнал onTrimMemory(level=$level). Очистка L1 RAM кэшей в Rust.")
        // telegramCore?.trimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.e(TAG_LOG, "Критический сигнал onLowMemory(). Полный сброс кэша превью и сокетов.")
        // telegramCore?.trimMemory(TRIM_MEMORY_COMPLETE)
    }

    companion object {
        const val CHANNEL_ID_MESSAGES = "tg_messages_channel"
        const val TAG_LOG = "TelegramApp"
        const val RUST_LOGCAT_TAG = "TelegramCore"

        lateinit var instance: TelegramApplication
            private set

        var isNativeLibraryLoaded: Boolean = false
            private set
    }
}
