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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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

  private var pressBackExitJob: Job? = null
  private var backPressedOnce = false

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    requestNotificationPermission()
    enableEdgeToEdge()

    setContent {
      val settingsViewModel: NewSettingsViewModel = koinViewModel()
      val currentTheme by settingsViewModel.theme.collectAsState()
//            val mainApp = LocalContext.current.applicationContext as MainApplication

//          settingsViewModel.getThemeValue()
      YkisPAMTheme(
        appTheme = currentTheme ?: "system"
//                appTheme = mainApp.theme.value,
      ) {
        val windowSize = calculateWindowSizeClass(this)
        val displayFeatures = calculateDisplayFeatures(this)
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .safeDrawingPadding(),
          ) {
            YkisPamApp(
              windowSize = windowSize,
              displayFeatures = displayFeatures
            )
          }
        }
      }
    }
    onBackPressedDispatcher.addCallback(this) {
      pressBackExitJob?.cancel()

      if (backPressedOnce) {
        finish()
        return@addCallback
      }

      Toast.makeText(applicationContext, getText(R.string.exit_app), Toast.LENGTH_SHORT)
        .show()

      backPressedOnce = true

      pressBackExitJob = lifecycleScope.launch {
        delay(2000)

        backPressedOnce = false
      }
    }
    addFcmToken()
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
          0
        )
      }
    }
  }

}
