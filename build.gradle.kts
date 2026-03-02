buildscript {
  dependencies {
    classpath(libs.google.services)
  }
}
plugins {
  alias(libs.plugins.androidApplication) apply false
  alias(libs.plugins.kotlinCompose) apply false
  alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.crashlytics) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.kotzilla) apply false
//  alias(libs.plugins.ktor) apply false


}
