/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

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
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.logger.Level

class MainApplication : Application() {
  val theme = mutableStateOf("system")

  override fun onCreate() {
    super.onCreate()
    Firebase.initialize(this)

    // 2. Настраиваем App Check (Play Integrity для продакшена)
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
      PlayIntegrityAppCheckProviderFactory.getInstance()
    )

    // Just start Koin!
    startKoin {
      androidContext(this@MainApplication)
      androidLogger(level = Level.ERROR)
//      analytics()
//      kotzillaLogger()
//      monitoring()  // Recommended for all platforms
      modules(appModule, dataModule, firebaseModule, domainModule, viewModelsModule)

    }
  }
}

