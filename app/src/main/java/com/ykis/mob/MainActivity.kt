package com.ykis.mob

import android.Manifest
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
class MainActivity : ComponentActivity() {

  private var pressBackExitJob: Job? = null
  private var backPressedOnce = false

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    requestNotificationPermission()
    enableEdgeToEdge()

    // 1. Считываем chatId из Intent (если приложение было закрыто)
    val startChatId = intent.getStringExtra("chatId")
    if (!startChatId.isNullOrEmpty()) {
      Log.d("YkisLog", "MainActivity: [START] Открываем чат из пуша: $startChatId")
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
            // 2. Передаем ID чата в основной компонент навигации
            initialChatId = startChatId
          )
        }
      }
    }

    setupDoubleBackExit()
    addFcmToken()
  }

  /**
   * 3. Обработка уведомления, если приложение уже открыто в фоне
   */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val chatId = intent.getStringExtra("chatId")
    if (!chatId.isNullOrEmpty()) {
      Log.d("YkisLog", "MainActivity: [NEW_INTENT] Переход в чат: $chatId")
      // Здесь можно вызвать метод навигации через ViewModel или EventBus
      // Например: appState.navigateToChat(chatId)
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
          101
        )
      }
    }
  }

  // Не забудь добавить метод addFcmToken() или импортировать его
}

