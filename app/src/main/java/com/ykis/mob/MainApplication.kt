package com.ykis.mob

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.initialize
import com.ykis.mob.di.appModule
import com.ykis.mob.di.dataModule
import com.ykis.mob.di.domainModule
import com.ykis.mob.di.firebaseModule
import com.ykis.mob.di.viewModelsModule
import io.kotzilla.sdk.analytics.koin.analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.extension.coroutinesEngine
import org.koin.core.logger.Level

class
MainApplication : Application() {
  val theme = mutableStateOf("system")

  // Создаем scope для фоновых задач инициализации
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onCreate() {
    super.onCreate()
// Firebase App должен быть инициализирован синхронно для Koin
    Firebase.initialize(this)

    // А вот проверку целостности (App Check) можно увести в фон через ваш scope
    applicationScope.launch {
      FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance()
      )
    }

    startKoin {
      androidContext(this@MainApplication)
      androidLogger(level = Level.DEBUG)
      analytics()
      coroutinesEngine(Dispatchers.IO)

      modules(appModule, dataModule, firebaseModule, domainModule, viewModelsModule)
    }

  }
}
