package com.ykis.mob

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
    createNotificationChannel() // КРИТИЧНО: создаем канал ДО запроса разрешений
    requestNotificationPermission()

    enableEdgeToEdge()

    // 2. Считываем данные из пуша (если приложение было убито)
    val startChatId = intent.getStringExtra("chatId")
    if (!startChatId.isNullOrEmpty()) {
      processChatDeepLink(startChatId) // Вызываем обработку
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
          YkisPamApp(
            windowSize = windowSize,
            displayFeatures = displayFeatures,
            initialChatId = startChatId
          )
        }
      }
    }

    setupDoubleBackExit()

    // 3. Регистрация токена для MySQL базы (чтобы сервер знал куда слать)
    addFcmToken()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    val chatId = intent.getStringExtra("chatId")
    if (!chatId.isNullOrEmpty()) {
      Log.d("YkisLog", "MainActivity: [NEW_INTENT] Получен chatId: $chatId")

      // 1. Извлекаем ID квартиры из строки пути (OSBB_3_1336_UID -> берем 1336)
      val parts = chatId.split("_")
      if (parts.size >= 3) {
        val addressIdFromPush = parts[parts.size - 2].toIntOrNull() ?: 0

        if (addressIdFromPush != 0) {
          // Получаем ViewModel-и через Koin (убедись, что они доступны в MainActivity)
          val apartmentViewModel: ApartmentViewModel by inject()
          val chatViewModel: ChatViewModel by inject()

          Log.d("YkisLog", "MainActivity: [PUSH_REDIRECT] Переключение на квартиру: $addressIdFromPush")

          // 2. КРИТИЧЕСКИЙ ШАГ: Меняем активную квартиру
          apartmentViewModel.setAddressId(addressIdFromPush)

          // 3. Устанавливаем "сигнал" для навигации в Compose
          // Мы передаем chatId во ViewModel, а RootNavGraph (или YkisPamApp)
          // увидит это через LaunchedEffect и сделает navController.navigate
          chatViewModel.setPendingPushChatId(chatId)
        }
      }
    }
  }


  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channelId = "chat_messages"
      val name = "Повідомлення чату"
      val importance = NotificationManager.IMPORTANCE_HIGH
      val channel = NotificationChannel(channelId, name, importance).apply {
        description = "Сповіщення про нові повідомлення в системі"
        // Включаем вибрацию и звук для HIGH важности
        enableLights(true)
        lightColor = android.graphics.Color.RED
        enableVibration(true)
      }
      val notificationManager = getSystemService(NotificationManager::class.java)
      notificationManager.createNotificationChannel(channel)
      Log.d("YkisLog", "MainActivity: [NOTIF] Канал 'chat_messages' создан")
    }
  }

  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val hasPermission = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED

      if (!hasPermission) {
        Log.d("YkisLog", "MainActivity: [NOTIF] Запрос разрешения на уведомления")
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          101
        )
      }
    }
  }

  private fun addFcmToken() {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
      if (!task.isSuccessful) {
        Log.w("YkisLog", "MainActivity: [FCM] Ошибка получения токена", task.exception)
        return@addOnCompleteListener
      }
      val token = task.result
      Log.d("YkisLog", "MainActivity: [FCM] Актуальный токен: $token")
      // Здесь отправь token в свою ViewModel -> Repository -> API (MySQL)
    }
  }
  private fun processChatDeepLink(chatId: String?) {
    if (chatId.isNullOrBlank()) return

    Log.d("YkisLog", "MainActivity: [DEEP_LINK] Обработка пути: $chatId")

    // Парсим адрес из строки (второе число с конца: OSBB_3_1336_UID)
    val parts = chatId.split("_")
    if (parts.size >= 3) {
      val addressId = parts[parts.size - 2].toIntOrNull() ?: 0
      if (addressId != 0) {
        // КРИТИЧНО: Переключаем квартиру во ViewModel
        // Тебе нужно получить доступ к apartmentViewModel (через Koin или inject)
        val apartmentViewModel: ApartmentViewModel by inject()
        apartmentViewModel.setAddressId(addressId)
        Log.d("YkisLog", "MainActivity: [DEEP_LINK] Квартира переключена на $addressId")
      }
    }
  }

  private fun setupDoubleBackExit() {
    onBackPressedDispatcher.addCallback(this) {
      if (backPressedOnce) {
        finish()
        return@addCallback
      }
      Toast.makeText(applicationContext, getString(R.string.exit_app), Toast.LENGTH_SHORT).show()
      backPressedOnce = true
      pressBackExitJob?.cancel()
      pressBackExitJob = lifecycleScope.launch {
        delay(2000)
        backPressedOnce = false
      }
    }
  }
}


