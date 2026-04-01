package com.ykis.mob

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

class MainActivity : ComponentActivity() {

  // Для логики выхода: храним состояние нажатия и ссылку на корутину сброса
  private var pressBackExitJob: Job? = null
  private var backPressedOnce = false

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    // 1. Установка Splash Screen (экран загрузки) до super.onCreate
    installSplashScreen()
    super.onCreate(savedInstanceState)

    // 2. Проверка разрешений на уведомления (обязательно для Android 13+)
    requestNotificationPermission()

    // 3. Включение отрисовки "от края до края" (под статус-баром и навигацией)
    enableEdgeToEdge()

    setContent {
      val settingsViewModel: NewSettingsViewModel = koinViewModel()
      val currentTheme by settingsViewModel.theme.collectAsState()

      YkisPAMTheme(appTheme = currentTheme ?: "system") {
        val windowSize = calculateWindowSizeClass(this)
        val displayFeatures = calculateDisplayFeatures(this)

        // Surface должен занимать весь экран БЕЗ отступов
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          // Передаем управление в главное приложение
          YkisPamApp(
            windowSize = windowSize,
            displayFeatures = displayFeatures
          )
        }
      }
    }


    // 4. Логика двойного клика кнопки "Назад" для выхода
    setupDoubleBackExit()

    // 5. Регистрация токена Firebase Cloud Messaging
    addFcmToken()
  }

  /**
   * Настройка обработчика кнопки "Назад".
   * Если нажато один раз — показываем подсказку. Если второй раз в течение 2 сек — выходим.
   */
  private fun setupDoubleBackExit() {
    onBackPressedDispatcher.addCallback(this) {
      if (backPressedOnce) {
        finish() // Закрываем активность
        return@addCallback
      }

      // Показываем сообщение (текст берется из ресурсов)
      Toast.makeText(applicationContext, getString(R.string.exit_app), Toast.LENGTH_SHORT).show()

      backPressedOnce = true
      pressBackExitJob?.cancel() // Сбрасываем старый таймер, если он был

      // Запускаем таймер сброса состояния через 2 секунды
      pressBackExitJob = lifecycleScope.launch {
        delay(2000)
        backPressedOnce = false
      }
    }
  }

  /**
   * Запрос разрешений для Android 13 (API 33) и выше.
   * Без этого push-уведомления (о долгах или новых тарифах) не придут.
   */
  private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val hasPermission = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED

      if (!hasPermission) {
        ActivityCompat.requestPermissions(
          this,
          arrayOf(Manifest.permission.POST_NOTIFICATIONS),
          101 // Код запроса
        )
      }
    }
  }
}
