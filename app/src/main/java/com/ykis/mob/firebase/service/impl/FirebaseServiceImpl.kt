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
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.StorageException
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.entity.UserFirebase
import com.ykis.mob.firebase.service.repo.AuthStateResponse
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.OneTapSignInResponse
import com.ykis.mob.firebase.service.repo.ReloadUserResponse
import com.ykis.mob.firebase.service.repo.SendEmailVerificationResponse
import com.ykis.mob.firebase.service.repo.SendPasswordResetEmailResponse
import com.ykis.mob.firebase.service.repo.SignInWithGoogleResponse
import com.ykis.mob.firebase.service.repo.SignUpResponse
import com.ykis.mob.firebase.service.repo.addUserFirestoreResponse
import com.ykis.mob.ui.screens.appartment.ApartmentService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseServiceImpl(
  private val context: Context,
  private val auth: FirebaseAuth,      // Передаем напрямую
  private val db: FirebaseFirestore,     // Передаем напрямую
  private val apartmentService: ApartmentService,
  private val chatRepo: ChatRepository
) : FirebaseService {
  private val credentialManager by lazy { CredentialManager.create(context) }


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
      // 1. Создаем пользователя в Firebase Auth
      val authResult = auth.createUserWithEmailAndPassword(email, password).await()
      val user = authResult.user

      if (user != null) {
        // 2. Создаем профиль в Firestore и во внешней БД
        val dbResult = addUserFirestore()

        if (dbResult is Resource.Error) {
          // Извлекаем текст ошибки: если есть строковое сообщение — берем его,
          // если только ID ресурса — достаем строку из ресурсов через context
          val errorDescription = dbResult.message ?: dbResult.resourceMessage?.let {
            context.getString(it)
          } ?: "Невідома помилка БД"

          return@withContext Resource.Error(
            message = "Акаунт створено, але не вдалося зберегти профіль: $errorDescription"
          )
        }

        // 3. Отправляем письмо для верификации
        user.sendEmailVerification().await()

        Resource.Success(true)
      } else {
        Resource.Error(message = "Не вдалося отримати дані користувача")
      }
    } catch (e: Exception) {
      Resource.Error(message = e.localizedMessage ?: "Помилка реєстрації")
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
      val user = auth.currentUser
      val currentUid = user?.uid
      val userEmail = user?.email ?: ""

      if (currentUid == null) {
        emit(Resource.Error(message = "Користувач не авторизований"))
        return@flow
      }

      // 1. Удаляем из вашей внешней БД (MySQL) через ApartmentService
      // Делаем это первым, так как если ваш сервер упадет, мы не удалим Firebase-аккаунт
      val mysqlResult = apartmentService.deleteUserAccount(currentUid, userEmail)
        .filter { it !is Resource.Loading }
        .first()

      if (mysqlResult is Resource.Error) {
        emit(Resource.Error(
          resourceMessage = mysqlResult.resourceMessage ?: R.string.error_delete_account,
          message = mysqlResult.message
        ))
        return@flow
      }
      Log.d("YkisLog", "1. MySQL: User deleted successfully")

      // 2. Удаляем фото из Storage (игнорируем ошибку 404, если фото нет)
      try {
        chatRepo.storage.reference.child("avatars/$currentUid").delete().await()
        Log.d("YkisLog", "2. Storage: Photo deleted")
      } catch (e: Exception) {
        if (e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
          Log.d("YkisLog", "2. Storage: No photo found (skipping)")
        } else {
          Log.e("YkisLog", "2. Storage: Other error: ${e.message}")
        }
      }

      // 3. Удаляем данные из Realtime Database (ветка /users/$uid)
      try {
        chatRepo.realtime.getReference("users").child(currentUid).removeValue().await()
        Log.d("YkisLog", "3. Realtime DB: Data deleted")
      } catch (e: Exception) {
        Log.e("YkisLog", "3. Realtime DB Error: ${e.message}")
      }

      // 4. Удаляем профиль из Firestore
      db.collection("users").document(currentUid).delete().await()
      Log.d("YkisLog", "4. Firestore: Document deleted")

      // 5. Удаляем самого пользователя из Firebase Auth
      // Это ОБЯЗАТЕЛЬНО последний шаг, пока UID еще валиден для правил доступа
      user.delete().await()
      Log.d("YkisLog", "5. Auth: User account deleted from Firebase")

      // 6. Очищаем состояние Google Sign-In
      credentialManager.clearCredentialState(ClearCredentialStateRequest())

      emit(Resource.Success(true))

    } catch (e: FirebaseAuthRecentLoginRequiredException) {
      Log.e("YkisLog", "Auth: Re-authentication required. Force signing out...")

      // Автоматически выходим, так как сессия устарела для удаления
      auth.signOut()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())

      emit(Resource.Error(message = "Для видалення акаунту потрібно заново увійти в систему"))

    } catch (e: Exception) {
      Log.e("YkisLog", "revokeAccess() Global Error: ${e.message}")
      emit(Resource.Error(message = e.localizedMessage ?: "Помилка видалення аккаунту"))
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

  override suspend fun addUserFirestore(): addUserFirestoreResponse = withContext(Dispatchers.IO) {
    try {
      // 1. Получаем текущие данные пользователя
      val currentUser = auth.currentUser
      val currentUid = currentUser?.uid
      val userEmail = currentUser?.email ?: ""

      if (currentUid.isNullOrEmpty()) {
        Log.e("YkisLog", "addUserFirestore() Error: UID is null")
        return@withContext Resource.Error("UID is empty")
      }

      // Явно указываем <String, Any>, чтобы можно было класть и строки, и даты, и числа
      val userMap: HashMap<String, Any> = hashMapOf(
        "uid" to currentUid,
        "email" to userEmail,
        "displayName" to (currentUser.displayName ?: ""),
        "userRole" to "STANDARD_USER",
        "osbbId" to 0,
        "createdAt" to com.google.firebase.Timestamp.now()
      )


      // 2. Пишем в Firestore
      db.collection("users")
        .document(currentUid)
        .set(userMap, SetOptions.merge())
        .await()

      Log.d("YkisLog", "Firestore Success for $currentUid")

      // 3. Пишем в вашу внешнюю БД через ApartmentService
      // .filter { it !is Resource.Loading } — игнорируем состояние загрузки
      // .first() — ждем первого фактического результата (Success или Error) и останавливаем Flow
      val result = getSaveUserUidResult(currentUid, userEmail)

      if (result is Resource.Error) {
        return@withContext Resource.Error(
          resourceMessage = result.resourceMessage ?: R.string.error_add_user,
          message = result.message
        )
      }


      Log.d("YkisLog", "All databases updated successfully for $currentUid")
      Resource.Success(true)

    } catch (e: Exception) {
      Log.e("YkisLog", "addUserFirestore() Exception: ${e.message}")
      Resource.Error(e.localizedMessage ?: "Process error")
    }
  }

  private suspend fun getSaveUserUidResult(uid: String, email: String): Resource<GetSimpleResponse> {
    return apartmentService.saveUserUid(uid, email)
      .filter { it !is Resource.Loading }
      .first() // Здесь .first() заберет Success или Error и закроет поток
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

  override suspend fun reloadFirebaseUser(): ReloadUserResponse = withContext(Dispatchers.IO) {
    try {
      val user = auth.currentUser
      if (user != null) {
        // Обновляем локальное состояние пользователя данными с сервера Firebase
        user.reload().await()

        // Возвращаем Success. В качестве данных передаем статус верификации email,
        // так как чаще всего reload() вызывают именно для проверки подтверждения почты.
        Resource.Success(user.isEmailVerified)
      } else {
        // Если пользователя нет в Auth, возвращаем ошибку
        Resource.Error(message = "Користувача не знайдено")
      }
    } catch (e: Exception) {
      Log.e("YkisLog", "reloadFirebaseUser() Error: ${e.message}")
      Resource.Error(message = e.localizedMessage ?: "Помилка оновлення профілю")
    }
  }



  override suspend fun updateUserRoleAndPermissions(
    uid: String,
    userRole: String,
    osbbId: Int?
  ) {
    try {
      val updates = mutableMapOf<String, Any>(
        "userRole" to userRole // Ключ "userRole" как в твоем методе получения
      )

      // Сохраняем привязку для админа ОСББ
      if (userRole == "OSBB" && osbbId != null) {
        updates["osbbId"] = osbbId
      }

      // Используем уже имеющуюся переменную db вместо getInstance()
      db.collection("users")
        .document(uid)
        .set(updates, SetOptions.merge())
        .await()

      Log.d("FirebaseService", "Permissions updated for $uid: $userRole")
    } catch (e: Exception) {
      // Здесь стоит вызвать твой logService.logNonFatalCrash(e)
      throw e
    }
  }

  override suspend fun getUserProfile(): UserFirebase = withContext(Dispatchers.IO) {
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid ?: ""

    try {
      // Запрос к документу пользователя в Firestore
      val snapshot = db.collection("users").document(currentUid).get().await()

      // Читаем данные из документа (userRole и osbbId)
      val roleStr = snapshot.getString("userRole") ?: "STANDARD_USER"
      val osbbIdFromDb = snapshot.getLong("osbbId")?.toInt() ?: 0

      UserFirebase(
        uid = currentUid,
        email = currentUser?.email ?: "",
        isEmailVerification = currentUser?.isEmailVerified ?: false,
        name = currentUser?.displayName,
        provider = currentUser?.providerId,
        phone = currentUser?.phoneNumber,
        // Маппинг строковой роли на Enum
        userRole = when (roleStr) {
          "STANDARD_USER"   -> UserRole.StandardUser
          "WATER_SERVICE"   -> UserRole.VodokanalUser
          "WARM_SERVICE"    -> UserRole.YtkeUser
          "GARBAGE_SERVICE" -> UserRole.TboUser
          "OSBB"            -> UserRole.OsbbUser
          else              -> UserRole.StandardUser
        },
        osbbId = osbbIdFromDb
      )
    } catch (e: Exception) {
      // Если документа еще нет (сразу после регистрации)
      UserFirebase(
        uid = currentUid,
        email = currentUser?.email ?: "",
        userRole = UserRole.StandardUser,
        osbbId = 0
      )
    }
  }

 // Геттеры для UID, Email, DisplayName
  override suspend fun getUid(): String = uid
  override suspend fun getEmail(): String = email
  override suspend fun getDisplayName(): String = displayName

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
