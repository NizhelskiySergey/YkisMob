import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeHotReload)
  alias(libs.plugins.androidMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.koin.compiler)
//  alias(libs.plugins.ksp)
}

kotlin {

  androidLibrary {
    namespace = "compose.project.ykis"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_21)
    }

    androidResources {
      enable = true
    }
  }

  listOf(
    iosArm64(),
    iosSimulatorArm64()
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.activity.compose)
      //koin
      implementation(project.dependencies.platform(libs.koin.bom))
      implementation(libs.koin.android)
      implementation(libs.koin.compose)


    }
    commonMain {
      // Добавляем путь к сгенерированным файлам KSP
//      kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodelCompose)
      implementation(libs.androidx.lifecycle.runtimeCompose)
      //koin

      implementation(project.dependencies.platform(libs.koin.bom))
      implementation(libs.koin.core)
      implementation(libs.koin.annotations)
      implementation(libs.koin.compose)
      implementation(libs.koin.compose.viewmodel)
      implementation(libs.koin.compose.viewmodel.navigation )

    }
  }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.koin.test)
    }
  }
  }
//
//tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
//  dependsOn("kspCommonMainKotlinMetadata")
//}
dependencies {
//koin

  androidRuntimeClasspath(libs.compose.uiTooling)
}

