package com.ykis.mob.ui.screens.auth.sign_in

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.ykis.mob.core.Resource
import com.ykis.mob.core.ext.isValidEmail
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.firebase.messaging.addFcmToken
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.firebase.service.repo.OneTapSignInResponse
import com.ykis.mob.firebase.service.repo.SignInResponse
import com.ykis.mob.firebase.service.repo.SignInWithGoogleResponse
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.navigation.SignUpScreen
import com.ykis.mob.ui.navigation.VerifyEmailScreen
import com.ykis.mob.ui.screens.auth.sign_in.components.SingInUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.ykis.mob.R.string as AppText
class SignInViewModel(
  private val firebaseService: FirebaseService,
  logService: LogService
) : BaseViewModel(logService) {

  private val email get() = singInUiState.email
  private val password get() = singInUiState.password

  var singInUiState by mutableStateOf(SingInUiState())
    private set

  var oneTapSignInResponse by mutableStateOf<OneTapSignInResponse>(Resource.Success(null))
    private set

  // Стейт для лоадера Google
  var signInWithGoogleResponse by mutableStateOf<SignInWithGoogleResponse>(Resource.Success(false))
    private set

  var signInResponse by mutableStateOf<SignInResponse>(Resource.Success(false))
    private set

  private val isEmailVerified get() = firebaseService.currentUser?.isEmailVerified ?: false
  init {
    Log.d("YkisLog", "SignInViewModel: [INIT_START]")
  }
  fun onEmailChange(newValue: String) {
    singInUiState = singInUiState.copy(email = newValue)
  }

  fun onPasswordChange(newValue: String) {
    singInUiState = singInUiState.copy(password = newValue)
  }

  // --- ОБЫЧНЫЙ ВХОД ---
  fun onSignInClick(openScreen: (String) -> Unit) {
    val methodName = "SignInVM.onSignInClick"
    if (!email.isValidEmail()) {
      Log.w("YkisLog", "$methodName: [VALIDATION_ERROR] Некорректный email")
      SnackbarManager.showMessage(AppText.email_error)
      return
    }

    if (password.isBlank()) {
      Log.w("YkisLog", "$methodName: [VALIDATION_ERROR] Пустой пароль")
      SnackbarManager.showMessage(AppText.empty_password_error)
      return
    }

    launchCatching {
      Log.d("YkisLog", "$methodName: [START] Вход для $email")
      signInResponse = Resource.Loading()

      firebaseService.firebaseSignInWithEmailAndPassword(email, password)
      addFcmToken()

      Log.d("YkisLog", "$methodName: [SUCCESS] Verified: $isEmailVerified")
      signInResponse = Resource.Success(true)

      if (isEmailVerified) {
        openScreen(Graph.APARTMENT)
      } else {
        openScreen(VerifyEmailScreen.route)
      }
    }
  }

  // --- ВОССТАНОВЛЕНИЕ ПАРОЛЯ ---
  fun onForgotPasswordClick() {
    if (!email.isValidEmail()) {
      SnackbarManager.showMessage(AppText.email_error)
      return
    }

    launchCatching {
      Log.d("YkisLog", "SignInVM: [RECOVERY] Запрос на почту $email")
      firebaseService.sendRecoveryEmail(email)
      SnackbarManager.showMessage(AppText.recovery_email_sent)
    }
  }

  fun onSignUpClick(openScreen: (String) -> Unit) {
    Log.d("YkisLog", "SignInVM: [NAVIGATE] Переход на регистрацию")
    openScreen(SignUpScreen.route)
  }

  // --- GOOGLE AUTH LOGIC ---
  suspend fun signInAndLinkWithGoogle(idToken: String) {
    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
    val currentUser = Firebase.auth.currentUser

    if (currentUser == null) {
      Log.d("YkisLog", "SignInVM.linkGoogle: [NEW_USER] Обычная авторизация")
      Firebase.auth.signInWithCredential(firebaseCredential).await()
    } else {
      Log.d("YkisLog", "SignInVM.linkGoogle: [LINK] Привязка к текущему аккаунту")
      currentUser.linkWithCredential(firebaseCredential).await()
    }
  }

  fun onSignUpWithGoogle(credential: Credential, openAndPopUp: () -> Unit) {
    val methodName = "SignInVM.onGoogleLogin"

    viewModelScope.launch {
      try {
        // 1. ВКЛЮЧАЕМ ЛОАДЕР (Гарантируем состояние Loading в UI)
        signInWithGoogleResponse = Resource.Loading()
        Log.d("YkisLog", "$methodName: [START] Лоадер включен. Обработка данных Google...")

        if (credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

          // 2. Входим/Линкуем в Firebase
          Log.d("YkisLog", "$methodName: [PROCESS] Шаг 1: Авторизация Firebase...")
          signInAndLinkWithGoogle(googleIdTokenCredential.idToken)

          // 3. Сохраняем в Firestore (Здесь была задержка 8 секунд, лоадер продолжит крутиться)
          Log.d("YkisLog", "$methodName: [PROCESS] Шаг 2: Синхронизация БД и профиля...")
          val dbResult = firebaseService.addUserFirestore()

          if (dbResult is Resource.Error) {
            Log.e("YkisLog", "$methodName: [ERROR] Ошибка при сохранении профиля")
            signInWithGoogleResponse = dbResult
            return@launch
          }

          // 4. ПОЛНЫЙ УСПЕХ (Лоадер выключится только сейчас)
          Log.d("YkisLog", "$methodName: [SUCCESS] Все проверки пройдены. Переход...")
          signInWithGoogleResponse = Resource.Success(true)
          openAndPopUp()

        } else {
          Log.e("YkisLog", "$methodName: [ERROR] Неверный тип данных от Google")
          signInWithGoogleResponse = Resource.Error("Помилка типу даних Google")
          SnackbarManager.showMessage("Помилка: невірний формат облікових даних")
        }
      } catch (e: Exception) {
        // 5. ОБРАБОТКА ОШИБОК
        Log.e("YkisLog", "$methodName: [CRITICAL] Ошибка: ${e.message}")
        signInWithGoogleResponse = Resource.Error(e.localizedMessage ?: "Невідома помилка")
        SnackbarManager.showMessage("Помилка входу Google: ${e.localizedMessage}")
      }
    }
  }

}
