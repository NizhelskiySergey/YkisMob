package com.ykis.mob.firebase.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ykis.mob.MainActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ykis.mob.R
import okhttp3.internal.notify
import org.checkerframework.checker.units.qual.C

class PushNotificationService : FirebaseMessagingService() {
  companion object {
    private const val TAG = "token_test"
  }

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    Log.d("token_test", "Refreshed token: $token")
    addFcmToken()
  }

  // В файле PushNotificationService.kt
  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    super.onMessageReceived(remoteMessage)

    val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
    val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
    val chatId = remoteMessage.data["chatId"] // ID чата, чтобы при клике открыть нужный

    if (title != null && body != null) {
      showNotification(title, body, chatId)
    }
  }
  private fun showNotification(title: String, body: String, chatId: String?) {
    val channelId = "chat_messages_channel"
    // Самый стабильный вариант
    // Вставляем 'this' как первый аргумент (Context)
// и 'NotificationManager::class.java' как второй
    val notificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java) as NotificationManager




    // 1. Создаем канал уведомлений (обязательно для Android 8.0+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        channelId,
        "Сообщения чата",
        NotificationManager.IMPORTANCE_HIGH
      )
      notificationManager.createNotificationChannel(channel)
    }

    // 2. Интент для открытия приложения при клике (обработка клика)
    val intent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("chatId", chatId) // Передаем ID чата, чтобы открыть его сразу
    }

    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )


    // 3. Сборка самого уведомления
    val notification = NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.drawable.baseline_notifications_active_24) // Замени на свою иконку
      .setContentTitle(title)
      .setContentText(body)
      .setAutoCancel(true) // Удалить из шторки после клика
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .build()

    // 4. Показываем (ID 101, можно использовать timestamp для уникальности)
    notificationManager.notify(System.currentTimeMillis().toInt(), notification)
  }

  private fun setContentTitle(title: String) {}
}
  


