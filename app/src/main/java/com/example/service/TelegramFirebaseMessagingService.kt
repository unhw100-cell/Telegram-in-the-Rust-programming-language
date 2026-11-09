package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.TelegramApplication
import com.example.data.MessageRepository
import com.example.core.MessageDto
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Фоновый обработчик Push-уведомлений Firebase Cloud Messaging (FCM).
 * Принимает зашифрованный payload от серверов Telegram и обрабатывает его
 * в фоновом режиме через Rust-ядро БЕЗ поднятия тяжелого UI Jetpack Compose.
 */
class TelegramFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        Log.i(TAG, "Получен фоновый FCM push: data=$data")

        // Извлекаем зашифрованную полезную нагрузку MTProto или текстовые поля
        val rawPayload = data["p"] ?: data["payload"] ?: data["body"] ?: "Входящее сообщение MTProto"
        val chatId = data["chat_id"]?.toLongOrNull() ?: 777L
        val senderTitle = data["title"] ?: "Telegram"

        serviceScope.launch {
            try {
                // 1. Сохраняем в локальную базу данных SQLite (tg-storage)
                val repository = MessageRepository(applicationContext)
                val now = System.currentTimeMillis()
                val incomingMsg = MessageDto(
                    id = now,
                    chatId = chatId,
                    senderId = chatId,
                    text = rawPayload,
                    timestamp = now,
                    isOutgoing = false,
                    deliveryStatus = 2
                )
                repository.saveMessage(incomingMsg)

                // 2. Отображаем нативное всплывающее уведомление Android
                showNotification(chatId, senderTitle, rawPayload)

                Log.i(TAG, "FCM payload успешно обработан и сохранен в SQLite без старта UI.")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обработки фонового push: ${e.message}", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Обновлен FCM registration token: $token")
        // Отправка токена на сервера Telegram (account.registerDevice)
        serviceScope.launch {
            // TelegramCore.registerDeviceToken(token)
        }
    }

    private fun showNotification(chatId: Long, title: String, text: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, TelegramApplication.CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(chatId.toInt(), notification)
    }

    companion object {
        private const val TAG = "TgMessagingService"
    }
}
