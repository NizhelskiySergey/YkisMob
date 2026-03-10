plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.googleServices)
  alias(libs.plugins.crashlytics)
  alias(libs.plugins.ksp)
  alias(libs.plugins.koin.compiler)
  alias(libs.plugins.kotzilla)
  alias(libs.plugins.kotlinSerialization)

}

android {
  namespace = "com.ykis.mob"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.ykis.mob"
    minSdk = 23
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
    multiDexEnabled = true
  }


  sourceSets.getByName("main") {
    kotlin.directories.add(file("build/generated/kotzilla/main/kotlin").toString())
    kotlin.directories.add(file("build/generated/ksp/debug/kotlin").toString())
  }



  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      buildConfigField("long", "VERSION_CODE", "${defaultConfig.versionCode}")
      buildConfigField("String", "VERSION_NAME", "\"${defaultConfig.versionName}\"")
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }


  buildFeatures {
    compose = true
    buildConfig = true
//        dataBinding = true
    viewBinding = true
  }
  packaging {
    resources {
      // Исключаем дублирующийся файл из сборки
      excludes += "/mozilla/public-suffix-list.txt"

      // Если возникнут похожие ошибки с другими файлами (например, лицензиями),
      // можно добавить и их:
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "/META-INF/DEPENDENCIES"
      excludes += "/META-INF/INDEX.LIST"
    }
    jniLibs {
      // Это заставит Gradle выравнивать .so файлы по границе 16 КБ
      useLegacyPackaging = false
    }
  }


}
ksp {
  arg("room.incremental", "false") // Временно выключите, чтобы проверить MissingType
  arg("room.generateKotlin", "true")
}
kotzilla {
  // Убедитесь, что этот флаг установлен в true
  composeInstrumentation = true
}

dependencies {

  //implementation(libs.androidx.material3.adaptive)
  implementation(libs.androidx.material3.adaptive.navigation.suite)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.firebase.functions)


  implementation(libs.androidx.room.external.antlr)

  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.foundation.layout)
  implementation(libs.androidx.compose.materialWindow)
  implementation(libs.androidx.compose.material.iconsExtended)
  implementation(libs.androidx.compose.runtime.livedata)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.accompanist.adaptive)

  androidTestImplementation(composeBom)
  androidTestImplementation(libs.androidx.compose.ui.test)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  implementation(libs.core.ktx)
  implementation(libs.constraintlayout)

  implementation(libs.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
//
//    firebase
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.crashlytics.buildtools)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.playintegrity)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.perf)
  implementation(libs.firebase.config)
  implementation(libs.firebase.messaging)
  implementation(libs.firebase.database)
  implementation(libs.firebase.storage)
  implementation(libs.play.services.auth)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.googleid)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services.auth)

  //ktor
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.android)
  implementation(libs.ktor.client.plugins)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.client.logging)
  implementation(libs.ktor.client.cio)




//koin
  implementation(project.dependencies.platform(libs.koin.bom))
  implementation(libs.koin.core)
  implementation(libs.koin.annotations)
  implementation(libs.koin.core.coroutines)
  implementation(libs.koin.android)
  implementation(libs.koin.compose)
  implementation(libs.koin.compose.viewmodel)
//  implementation(libs.kotzilla.sdk.android)
//  implementation(libs.kotzilla.sdk)
  implementation(libs.kotzilla.sdk.compose.android)


// Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.legacy.support.v4)
  ksp(libs.androidx.room.compiler)

  //coil
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)
  implementation(libs.androidx.splashscreen)
//
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.espresso.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.datastore.preferences)
  val cameraxVersion = "1.3.4"
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.lifecycle)


}

// Если вы используете KSP для Android (Android Specific Task)
tasks.matching { it.name.startsWith("ksp") && it.name.contains("Kotlin") }.configureEach {
  dependsOn("generateKotzillaConfig")
}
