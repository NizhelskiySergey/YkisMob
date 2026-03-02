package com.ykis.mob.ui.screens.auth.sign_in

import android.content.Context
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

  private val email
    get() = singInUiState.email
  private val password
    get() = singInUiState.password

  var singInUiState by mutableStateOf(SingInUiState())
    private set
  var oneTapSignInResponse by mutableStateOf<OneTapSignInResponse>(Resource.Success(null))
    private set

  var signInWithGoogleResponse by mutableStateOf<SignInWithGoogleResponse>(Resource.Success(false))
    private set

  var signInResponse by mutableStateOf<SignInResponse>(Resource.Success(false))


  private val isEmailVerified get() = firebaseService.currentUser?.isEmailVerified ?: false


  fun onEmailChange(newValue: String) {
    singInUiState = singInUiState.copy(email = newValue)
  }

  fun onPasswordChange(newValue: String) {
    singInUiState = singInUiState.copy(password = newValue)
  }


  fun onSignInClick(openScreen: (String) -> Unit) {
    if (!email.isValidEmail()) {
      SnackbarManager.showMessage(AppText.email_error)
      return
    }

    if (password.isBlank()) {
      SnackbarManager.showMessage(AppText.empty_password_error)
      return
    }

    launchCatching {
      firebaseService.firebaseSignInWithEmailAndPassword(email, password)
      addFcmToken()
      if (isEmailVerified) {
        openScreen(Graph.APARTMENT)
      } else {
        openScreen(VerifyEmailScreen.route)
      }

    }

  }

  fun onForgotPasswordClick() {
    if (!email.isValidEmail()) {
      SnackbarManager.showMessage(AppText.email_error)
      return
    }

    launchCatching {
      firebaseService.sendRecoveryEmail(email)
      SnackbarManager.showMessage(AppText.recovery_email_sent)
    }
  }

  fun oneTapSignIn(context: Context, openAndPopUp: () -> Unit) {
    viewModelScope.launch {
      oneTapSignInResponse = Resource.Loading()
      val result = firebaseService.oneTapSignInWithGoogle(context)
      oneTapSignInResponse = result

      if (result is Resource.Success && result.data != null) {
        // Если системное окно вернуло данные, обрабатываем их
        onSignUpWithGoogle(result.data.credential, openAndPopUp)
      } else if (result is Resource.Error) {
        SnackbarManager.showMessage(result.resourceMessage)
      }
    }
  }


  fun signInWithGoogle(googleCredential: AuthCredential) {
    launchCatching {
      oneTapSignInResponse = Resource.Loading()
      signInWithGoogleResponse = firebaseService.firebaseSignInWithGoogle(googleCredential)
    }
  }

  fun onSignUpClick(openScreen: (String) -> Unit) {
    launchCatching {
      openScreen(SignUpScreen.route)
    }
  }

  suspend fun signInAndLinkWithGoogle(idToken: String) {
    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

    val currentUser = Firebase.auth.currentUser
    if (currentUser == null) {
      // Авторизація користувача
      Firebase.auth.signInWithCredential(firebaseCredential).await()
    } else {
      // Прив'язка до існуючого облікового запису
      currentUser.linkWithCredential(firebaseCredential).await()
    }
  }

  fun onSignUpWithGoogle(credential: Credential, openAndPopUp: () -> Unit) {
    viewModelScope.launch {
      try {
        // Проверка типа данных (Google ID Token)
        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

          // Входим в Firebase и линкуем аккаунт
          signInAndLinkWithGoogle(googleIdTokenCredential.idToken)

          // Сохраняем в БД
          firebaseService.addUserFirestore()

          openAndPopUp()
        } else {
          SnackbarManager.showMessage("Невдалося зареєструватись з Google аккаунтом")
        }
      } catch (e: Exception) {
        SnackbarManager.showMessage("Ошибка входа Google: ${e.localizedMessage}")
      }
    }
  }

}
