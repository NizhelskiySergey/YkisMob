package com.ykis.mob.firebase.service.impl

import android.content.Context
import android.util.Log
import androidx.compose.ui.util.trace
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.service.repo.AuthStateResponse
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.OneTapSignInResponse
import com.ykis.mob.firebase.service.repo.ReloadUserResponse
import com.ykis.mob.firebase.service.repo.SendEmailVerificationResponse
import com.ykis.mob.firebase.service.repo.SendPasswordResetEmailResponse
import com.ykis.mob.firebase.service.repo.SignInResponse
import com.ykis.mob.firebase.service.repo.SignInWithGoogleResponse
import com.ykis.mob.firebase.service.repo.SignUpResponse
import com.ykis.mob.firebase.service.repo.addUserFirestoreResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

class FirebaseServiceImpl(
  private val context: Context,
  private val auth: FirebaseAuth,      // Передаем напрямую
  private val dbLazy: Lazy<FirebaseFirestore>     // Передаем напрямую
) : FirebaseService {
  private val credentialManager by lazy { CredentialManager.create(context) }
  private val db by dbLazy


  // --- Свойства пользователя ---
  override val isUserAuthenticatedInFirebase: Boolean get() = auth.currentUser != null
  override val uid: String get() = auth.currentUser?.uid ?: ""
  override val hasUser: Boolean get() = auth.currentUser != null
  override val isEmailVerified: Boolean get() = auth.currentUser?.isEmailVerified ?: false
  override val currentUser: FirebaseUser? get() = auth.currentUser
  override val displayName: String get() = auth.currentUser?.displayName ?: ""
  override val email: String get() = auth.currentUser?.email ?: ""
  override val photoUrl: String get() = auth.currentUser?.photoUrl?.toString() ?: ""
  override val providerId: String get() = auth.currentUser?.providerData?.getOrNull(1)?.providerId ?: ""

  // --- Авторизация (Email/Password) ---
  override suspend fun firebaseSignInWithEmailAndPassword(email: String, password: String) {
    withContext(Dispatchers.IO) {
      auth.signInWithEmailAndPassword(email, password).await()
    }
  }
  override suspend fun firebaseSignUpWithEmailAndPassword(
    email: String,
    password: String
  ): SignUpResponse = withContext(Dispatchers.IO) {
    try {
      // 1. Создаем пользователя
      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val user = authResult.user

      if (user != null) {
        // 2. Создаем профиль в Firestore и ЖДЕМ результата
        val dbResult = addUserFirestore()

        if (dbResult is Resource.Error) {
          // Если база не ответила, уведомляем об ошибке (хотя аккаунт в Auth уже есть)
          return@withContext Resource.Error("Акаунт створено, але не вдалося зберегти профіль: ${dbResult.resourceMessage}")
        }

        // 3. Отправляем письмо
        user.sendEmailVerification().await()

        Resource.Success(true)
      } else {
        Resource.Error("Не вдалося отримати дані користувача")
      }
    } catch (e: Exception) {
      Resource.Error(e.localizedMessage ?: "Помилка реєстрації")
    }
  }




  // Вход через Google (Credential Manager)
  override suspend fun oneTapSignInWithGoogle(context: Context): OneTapSignInResponse = try {
    val credentialManager = CredentialManager.create(context)

    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(false)
      .setServerClientId(context.getString(R.string.web_client_id))
      .setAutoSelectEnabled(true)
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    // Вызов системного диалога (suspend)
    val result = credentialManager.getCredential(context = context, request = request)

    Resource.Success(result)
  } catch (e: GetCredentialException) {
    Resource.Error(e.message ?: "Credential Manager Error")
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Unknown Error")
  }


  override fun signOut(): Flow<Resource<Boolean>> = flow {
    emit(Resource.Loading())
    try {
      auth.signOut()
      // Очищаем состояние Credential Manager
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
      emit(Resource.Success(true))
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Google Sign-In failed"))
    }
  }.flowOn(Dispatchers.IO)


  override suspend fun firebaseSignInWithGoogle(googleCredential: AuthCredential): SignInWithGoogleResponse = try {
    withContext(Dispatchers.IO) {
      auth.signInWithCredential(googleCredential).await()
      Resource.Success(true)
    }
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Firebase Google Auth failed")
  }


  override fun revokeAccess(): Flow<Resource<Boolean>> = flow {
    emit(Resource.Loading())
    try {
      // 1. Удаляем пользователя из Firebase Auth
      auth.currentUser?.delete()?.await()

      // 2. Очищаем состояние Credential Manager (вместо oneTapClient.signOut)
      val credentialManager = CredentialManager.create(context)
      credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())

      emit(Resource.Success(true))
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Помилка видалення аккаунту"))
    }
  }.flowOn(Dispatchers.IO)

  override fun revokeAccessEmail(): Flow<Resource<Boolean>> = revokeAccess()

  override suspend fun sendEmailVerification(): SendEmailVerificationResponse = try {
    auth.currentUser?.sendEmailVerification()?.await()
    Resource.Success(true)
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Verification failed")
  }

  override suspend fun sendPasswordResetEmail(email: String): SendPasswordResetEmailResponse = try {
    auth.sendPasswordResetEmail(email).await()
    Resource.Success(true)
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Reset failed")
  }

  // --- Работа с Firestore ---
  override suspend fun addUserFirestore(): addUserFirestoreResponse = withContext(Dispatchers.IO) {
    try {
      // Убедимся, что UID не пустой, прежде чем писать в БД
      val currentUid = uid
      if (currentUid.isEmpty()) return@withContext Resource.Error("UID is empty")

      val userMap = hashMapOf(
        "uid" to currentUid,
        "email" to email,
        "displayName" to displayName,
        "role" to UserRole.StandardUser.codeName, // Добавляем роль
        "createdAt" to com.google.firebase.Timestamp.now() // Серверное время
      )

      // Записываем данные в документ с ID равным UID пользователя
      db.collection("users").document(currentUid).set(userMap).await()

      Resource.Success(true)
    } catch (e: Exception) {
      Resource.Error(e.localizedMessage ?: "Firestore write error")
    }
  }


  // --- Остальные методы ---
  override fun getAuthState(viewModelScope: CoroutineScope): AuthStateResponse {
    return callbackFlow {
      val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        // trySend безопаснее обычного emit в callbackFlow
        trySend(firebaseAuth.currentUser != null)
      }

      auth.addAuthStateListener(listener)

      // Гарантируем отписку, чтобы не было утечек памяти
      awaitClose {
        auth.removeAuthStateListener(listener)
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000), // Держит поток живым 5 сек после ухода с экрана
      initialValue = auth.currentUser != null
    )
  }


  override fun getProvider(viewModelScope: CoroutineScope): String = providerId

  override suspend fun reloadFirebaseUser(): ReloadUserResponse {
    return try {
      val user = auth.currentUser
      if (user != null) {
        user.reload().await() // Обновляем данные с сервера
        // Возвращаем успех, только если пользователь всё еще залогинен
        Resource.Success(user.isEmailVerified)
      } else {
        Resource.Error("Пользователь не найден")
      }
    } catch (e: Exception) {
      Resource.Error(e.localizedMessage ?: "Ошибка обновления профиля")
    }
  }


  override suspend fun getUserRole(): UserRole = withContext(Dispatchers.IO) {
    try {
      val snapshot = db.collection("users")
        .document(uid)
        .get()
        .await()

      val roleString = snapshot.getString("role") ?: "standard_user"

      // Маппим строку из БД в наш Enum
      UserRole.entries.find { it.codeName == roleString } ?: UserRole.StandardUser
    } catch (e: Exception) {
      Log.e("FirebaseService", "Error fetching role: ${e.message}")
      UserRole.StandardUser // Возвращаем дефолт при ошибке
    }
  }


  // Геттеры для UID, Email, DisplayName
  override suspend fun getUid(): String = uid
  override suspend fun getEmail(): String = email
  override suspend fun getDisplayName(): String = displayName
  override suspend fun getOsbbRoleId(): Int? = null

  // Пустые методы (реализуйте по необходимости)
  override suspend fun authenticate(email: String, password: String) {}
  override suspend fun sendRecoveryEmail(email: String) {}
  //  override suspend fun linkAccount(email: String, password: String) {}
  override suspend fun deleteAccount() { auth.currentUser?.delete()?.await() }
  override suspend fun firebaseSignUpWithGoogle2(googleCredential: AuthCredential) { firebaseSignInWithGoogle(googleCredential) }



  // end google auth
  override suspend fun linkAccount(email: String, password: String): Unit =
    trace(LINK_ACCOUNT_TRACE) {
      val credential = EmailAuthProvider.getCredential(email, password)
      // Безопасный вызов через ?.
      auth.currentUser?.linkWithCredential(credential)?.await()
    }

  companion object {
    private const val LINK_ACCOUNT_TRACE = "linkAccount"
  }
}
