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

  private val _signUpResponse = MutableStateFlow<SignUpResponse>(Resource.Success(false))
  val signUpResponse = _signUpResponse.asStateFlow()

  // Стейт для верификации почты
  private val _sendEmailVerificationResponse = MutableStateFlow<SendEmailVerificationResponse>(Resource.Success(false))

  var signUpUiState = mutableStateOf(SignUpUiState())
    private set

  val email get() = signUpUiState.value.email
  private val password get() = signUpUiState.value.password
  private val repeatPassword get() = signUpUiState.value.repeatPassword

  val isEmailVerified get() = firebaseService.currentUser?.isEmailVerified ?: false

  init {
    launchCatching { configurationService.fetchConfiguration() }
  }

  // --- ВАЛИДАЦИЯ (Вынесена отдельно) ---
  private fun isInputValid(): Boolean {
    if (!email.isValidEmail()) {
      SnackbarManager.showMessage(AppText.email_error)
      return false
    }
    if (!password.isValidPassword()) {
      SnackbarManager.showMessage(AppText.password_error)
      return false
    }
    if (!password.passwordMatches(repeatPassword)) {
      SnackbarManager.showMessage(AppText.password_match_error)
      return false
    }
    return true
  }

  // --- РЕГИСТРАЦИЯ ---
  fun signUpWithEmailAndPassword(onSuccess: () -> Unit) {
    if (!isInputValid()) return

    launchCatching {
      _signUpResponse.value = Resource.Loading()

      // 1. Регистрируем пользователя
      val result = firebaseService.firebaseSignUpWithEmailAndPassword(email, password)
      _signUpResponse.value = result

      if (result is Resource.Success) {
        // 2. Сразу отправляем письмо верификации
        firebaseService.sendEmailVerification()

        // 3. Обновляем токен пушей, чтобы пользователь был на связи
        addFcmToken()

        onSuccess()
      }
    }
  }

  // Повторная отправка письма (если первое не дошло)
  fun repeatEmailVerified() {
    launchCatching {
      _sendEmailVerificationResponse.value = Resource.Loading()
      _sendEmailVerificationResponse.value = firebaseService.sendEmailVerification()
      SnackbarManager.showMessage(R.string.verify_email_message)
    }
  }

  fun onEmailChange(newValue: String) { signUpUiState.value = signUpUiState.value.copy(email = newValue) }
  fun onPasswordChange(newValue: String) { signUpUiState.value = signUpUiState.value.copy(password = newValue) }
  fun onRepeatPasswordChange(newValue: String) { signUpUiState.value = signUpUiState.value.copy(repeatPassword = newValue) }

  fun reloadUser(onSuccess: () -> Unit) {
    launchCatching {
      _reloadUserResponse.value = Resource.Loading()
      val result = firebaseService.reloadFirebaseUser()
      _reloadUserResponse.value = result

      if (result is Resource.Success && isEmailVerified) {
        addFcmToken() // Обновляем токен при успешном подтверждении
        onSuccess()
      }
    }
  }
}
