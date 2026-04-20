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

package com.ykis.mob.ui.screens.auth.sign_up

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.ext.isValidEmail
import com.ykis.mob.core.ext.isValidPassword
import com.ykis.mob.core.ext.passwordMatches
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.firebase.messaging.addFcmToken
import com.ykis.mob.firebase.service.repo.ConfigurationService
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.firebase.service.repo.ReloadUserResponse
import com.ykis.mob.firebase.service.repo.SendEmailVerificationResponse
import com.ykis.mob.firebase.service.repo.SignUpResponse
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.screens.auth.sign_up.components.SignUpUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.ykis.mob.R.string as AppText
class SignUpViewModel(
  private val firebaseService: FirebaseService,
  private val configurationService: ConfigurationService,
  logService: LogService
) : BaseViewModel(logService) {

  private val _reloadUserResponse = MutableStateFlow<ReloadUserResponse>(Resource.Success(false))
  val reloadUserResponse = _reloadUserResponse.asStateFlow()

  private val _signUpResponse = MutableStateFlow<SignUpResponse?>(null)
  val signUpResponse = _signUpResponse.asStateFlow()

  private val _sendEmailVerificationResponse = MutableStateFlow<SendEmailVerificationResponse>(Resource.Success(false))

  var signUpUiState = mutableStateOf(SignUpUiState())
    private set

  val email get() = signUpUiState.value.email
  // В SignUpViewModel
  val displayEmail: String
    get() = signUpUiState.value.email.ifBlank {
      firebaseService.currentUser?.email ?: ""
    }

  private val password get() = signUpUiState.value.password
  private val repeatPassword get() = signUpUiState.value.repeatPassword

  val isEmailVerified get() = firebaseService.currentUser?.isEmailVerified ?: false

  init {
    val methodName = "SignUpVM.init"
    launchCatching {
      Log.d("YkisLog", "$methodName: [START] Загрузка Remote Config")
      configurationService.fetchConfiguration()
      Log.d("YkisLog", "$methodName: [SUCCESS] Конфигурация готова")
    }
  }

  // --- ВАЛИДАЦИЯ ---
  private fun isInputValid(): Boolean {
    val methodName = "SignUpVM.validate"
    if (!email.isValidEmail()) {
      Log.w("YkisLog", "$methodName: Некорректный email: $email")
      SnackbarManager.showMessage(AppText.email_error)
      return false
    }
    if (!password.isValidPassword()) {
      Log.w("YkisLog", "$methodName: Пароль не прошел проверку сложности")
      SnackbarManager.showMessage(AppText.password_error)
      return false
    }
    if (!password.passwordMatches(repeatPassword)) {
      Log.w("YkisLog", "$methodName: Пароли не совпадают")
      SnackbarManager.showMessage(AppText.password_match_error)
      return false
    }
    return true
  }

  // --- РЕГИСТРАЦИЯ ---
  fun signUpWithEmailAndPassword(onSuccess: () -> Unit) {
    val methodName = "SignUpVM.signUp"
    if (!isInputValid()) return

    // 1. СБРОС СТЕЙТА перед началом, чтобы LaunchedEffect не видел старый Success
    _signUpResponse.value = null

    launchCatching {
      Log.d("YkisLog", "$methodName: [START] Регистрация для: $email")
      _signUpResponse.value = Resource.Loading()

      val result = firebaseService.firebaseSignUpWithEmailAndPassword(email, password)

      // 2. ЗАПИСЬ РЕЗУЛЬТАТА (это триггернет LaunchedEffect в UI)
      _signUpResponse.value = result

      when (result) {
        is Resource.Success -> {
          Log.d("YkisLog", "$methodName: [SUCCESS] Пользователь создан. Отправка email...")
          firebaseService.sendEmailVerification()
          addFcmToken()
          // 3. Вызываем onSuccess() только если LaunchedEffect на экране отсутствует.
          // Если на экране есть LaunchedEffect(signUpResponse), то onSuccess можно оставить пустым.
          onSuccess()
        }
        is Resource.Error -> {
          Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")
        }
        else -> {}
      }
    }
  }


  fun repeatEmailVerified() {
    val methodName = "SignUpVM.repeatVerify"
    val userEmail = firebaseService.currentUser?.email

    launchCatching {
      Log.d("YkisLog", "$methodName: [REQUEST] Отправка на $userEmail")
      _sendEmailVerificationResponse.value = Resource.Loading()

      val result = firebaseService.sendEmailVerification()
      _sendEmailVerificationResponse.value = result

      if (result is Resource.Success) {
        Log.d("YkisLog", "$methodName: [SUCCESS] Письмо отправлено")
        SnackbarManager.showMessage(R.string.verify_email_message)
      } else {
        // КРИТИЧЕСКИЙ ФИКС: Выводим реальную причину из Firebase (например, лимит запросов)
        val errorMsg = result.message ?: "Не вдалося відправити лист"
        Log.e("YkisLog", "$methodName: [ERROR] Причина: $errorMsg")
        SnackbarManager.showMessage(errorMsg)
      }
    }
  }


  fun onEmailChange(newValue: String) { signUpUiState.value = signUpUiState.value.copy(email = newValue) }
  fun onPasswordChange(newValue: String) { signUpUiState.value = signUpUiState.value.copy(password = newValue) }
  fun onRepeatPasswordChange(newValue: String) { signUpUiState.value = signUpUiState.value.copy(repeatPassword = newValue) }

  // Проверка статуса верификации
  fun reloadUser(onSuccess: () -> Unit) {
    val methodName = "SignUpVM.reloadUser"
    launchCatching {
      Log.d("YkisLog", "$methodName: [START] Проверка подтверждения почты...")
      _reloadUserResponse.value = Resource.Loading()
      val result = firebaseService.reloadFirebaseUser()
      _reloadUserResponse.value = result

      if (result is Resource.Success) {
        val verified = firebaseService.currentUser?.isEmailVerified == true
        Log.d("YkisLog", "$methodName: [RESULT] Почта подтверждена: $verified")

        if (verified) {
          addFcmToken()
          onSuccess()
        } else {
          SnackbarManager.showMessage("Пошта ще не підтверджена. Перевірте скриньку.")
        }
      }
    }
  }
}
