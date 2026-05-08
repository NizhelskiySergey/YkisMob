package com.ykis.mob

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.adaptive.calculateDisplayFeatures
import com.ykis.mob.firebase.messaging.addFcmToken
import com.ykis.mob.ui.YkisPamApp
import com.ykis.mob.ui.screens.settings.NewSettingsViewModel
import com.ykis.mob.ui.theme.YkisPAMTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.room.jarjarred.org.antlr.v4.runtime.misc.MurmurHash.finish
import com.google.firebase.messaging.FirebaseMessaging
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.chat.ChatViewModel
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
  private var pressBackExitJob: Job? = null
  private var backPressedOnce = false
  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    // 1. Инициализация уведомлений
    createNotificationChannel()
    requestNotificationPermission()
    enableEdgeToEdge()
    // 2. Считываем данные из пуша для холодного старта
    val startChatId = intent.getStringExtra("chatId")
    if (!startChatId.isNullOrEmpty()) {
      Log.i("YkisLog", "MainActivity: [COLD_START] Обнаружен chatId в пуше: $startChatId")
    }
    setContent {
      val settingsViewModel: NewSettingsViewModel = koinViewModel()
      val currentTheme by settingsViewModel.theme.collectAsState()
      YkisPAMTheme(appTheme = currentTheme ?: "system") {
        val windowSize = calculateWindowSizeClass(this)
        val displayFeatures = calculateDisplayFeatures(this)
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          // Важно: initialChatId обработает навигацию внутри Compose
          YkisPamApp(
            windowSize = windowSize,
            displayFeatures = displayFeatures,
            initialChatId = startChatId
          )
        }
      }
    }
    setupDoubleBackExit()
    addFcmToken()
    // 1. Принудительно чистим шторку уведомлений при открытии приложения
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancelAll()
    Log.d("YkisLog", "MainActivity: [CLEANUP] Шторка уведомлений очищена при старте")

    // 2. Синхронизируем бейдж на иконке (через Koin достаем ChatViewModel)
    try {
      val chatViewModel: ChatViewModel by inject()
      // Нам нужно вызвать метод обновления.
      // Но так как данные из Firebase могут еще не прийти (холодный старт),
      // иконка обновится сама чуть позже через subscribeToUnreadCount во ViewModel.
      // Здесь мы просто убеждаемся, что Badger готов.
    } catch (e: Exception) {
      Log.e("YkisLog", "MainActivity: [ERROR] Ошибка инициализации Badger: ${e.message}")
    }
  }
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent) // Перезаписываем интент для корректного считывания
    val chatId = intent.getStringExtra("chatId")
    if (!chatId.isNullOrEmpty()) {
      Log.i("YkisLog", "MainActivity: [NEW_INTENT] Получен клик по пушу (фон): $chatId")
      // Обработка переключения квартиры и сигнала для навигации
      processChatDeepLink(chatId)
    }
  }
  private fun processChatDeepLink(chatId: String?) {
    if (chatId.isNullOrBlank()) return
    Log.d("YkisLog", "MainActivity: [PROCESS_DEEP_LINK] Начало парсинга: $chatId")
    try {
      // Парсим адрес из строки (например: WATER_SERVICE_9999_1336_UID)
      val parts = chatId.split("_")
      if (parts.size >= 3) {
        // Извлекаем addressId (предпоследний элемент)
        val addressId = parts[parts.size - 2].toIntOrNull() ?: 0
        if (addressId != 0) {
          // Получаем ViewModel через Koin принудительно
          val apartmentViewModel: ApartmentViewModel by inject()
          val chatViewModel: ChatViewModel by inject()

          Log.d("YkisLog", "MainActivity: [REDIRECT] Переключаем квартиру на ID: $addressId")
          apartmentViewModel.setAddressId(addressId)

          Log.d("YkisLog", "MainActivity: [REDIRECT] Установка сигнала навигации для ChatId: $chatId")
          chatViewModel.setPendingPushChatId(chatId)
        } else {
          Log.w("YkisLog", "MainActivity: [PARSING_ERROR] Не удалось вычислить addressId из $chatId")
        }
      }
    } catch (e: Exception) {
      Log.e("YkisLog", "MainActivity: [FATAL_DEEP_LINK] Ошибка: ${e.message}")
    }
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancelAll()
  }
  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channelId = "chat_messages"
      val name = "Повідомлення чату"
      val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Сповіщення про нові повідомлення в системі"
        enableLights(true)
        lightColor = android.graphics.Color.RED
        enableVibration(true)
      }
      getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
      Log.d("YkisLog", "MainActivity: [NOTIF] Канал 'chat_messages' готов")
    }
  }
  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val status = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
      if (status != PackageManager.PERMISSION_GRANTED) {
        Log.d("YkisLog", "MainActivity: [NOTIF] Запрос прав на уведомления")
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }
  }
  private fun addFcmToken() {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        Log.d("YkisLog", "MainActivity: [FCM] Токен: ${task.result}")
      }
    }
  }
  private fun setupDoubleBackExit() {
    onBackPressedDispatcher.addCallback(this) {
      if (backPressedOnce) { finish(); return@addCallback }
      Toast.makeText(applicationContext, getString(R.string.exit_app), Toast.LENGTH_SHORT).show()
      backPressedOnce = true
      pressBackExitJob?.cancel()
      pressBackExitJob = lifecycleScope.launch { delay(2000); backPressedOnce = false }
    }
  }
}






