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
import com.google.firebase.firestore.FieldValue
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
  override val providerId: String
    get() = auth.currentUser?.providerData?.getOrNull(1)?.providerId ?: ""

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


  override suspend fun firebaseSignInWithGoogle(googleCredential: AuthCredential): SignInWithGoogleResponse =
    try {
      withContext(Dispatchers.IO) {
        auth.signInWithCredential(googleCredential).await()
        Resource.Success(true)
      }
    } catch (e: Exception) {
      Resource.Error(e.localizedMessage ?: "Firebase Google Auth failed")
    }


  override fun revokeAccess(): Flow<Resource<Boolean>> = flow {
    val methodName = "FirebaseServiceImpl.revokeAccess()"
    emit(Resource.Loading())

    try {
      val user = auth.currentUser
      val currentUid = user?.uid
      val userEmail = user?.email ?: ""

      if (currentUid == null) {
        Log.e("YkisLog", "$methodName: [ERROR] Пользователь null")
        emit(Resource.Error(message = "Користувач не авторизований"))
        return@flow
      }

      Log.d("YkisLog", "$methodName: [START] Удаление аккаунта для UID: $currentUid")

      // 1. MySQL (Внешняя БД)
      val mysqlResult = apartmentService.deleteUserAccount(currentUid, userEmail)
        .filter { it !is Resource.Loading }
        .first()

      if (mysqlResult is Resource.Error) {
        Log.e("YkisLog", "$methodName: [STEP 1 FAILED] MySQL Error")
        emit(Resource.Error(
          resourceMessage = mysqlResult.resourceMessage ?: R.string.error_delete_account,
          message = mysqlResult.message
        ))
        return@flow
      }
      Log.d("YkisLog", "$methodName: [STEP 1 SUCCESS] MySQL: Данные удалены")

      // 2. Storage (Аватарка)
      try {
        chatRepo.storage.reference.child("avatars/$currentUid").delete().await()
        Log.d("YkisLog", "$methodName: [STEP 2 SUCCESS] Storage: Фото удалено")
      } catch (e: Exception) {
        val is404 = e is StorageException && e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
        if (is404) {
          Log.d("YkisLog", "$methodName: [STEP 2 SKIP] Storage: Файл не найден")
        } else {
          Log.e("YkisLog", "$methodName: [STEP 2 ERROR] Storage: ${e.message}")
        }
      }

      // 3. Realtime Database (Ветка пользователя)
      try {
        chatRepo.realtime.getReference("users").child(currentUid).removeValue().await()
        Log.d("YkisLog", "$methodName: [STEP 3 SUCCESS] RTDB: Ветка удалена")
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [STEP 3 ERROR] RTDB: ${e.message}")
        // Важно: даже если тут ошибка 403, идем дальше, чтобы удалить Auth
      }

      // 4. Firestore (Профиль)
      db.collection("users").document(currentUid).delete().await()
      Log.d("YkisLog", "$methodName: [STEP 4 SUCCESS] Firestore: Документ удален")

      // 5. Firebase Auth (САМЫЙ ВАЖНЫЙ МОМЕНТ)
      Log.d("YkisLog", "$methodName: [STEP 5] Удаление из Firebase Auth...")
      user.delete().await()
      Log.d("YkisLog", "$methodName: [STEP 5 SUCCESS] Auth: Аккаунт стерт")

      // 6. Credential Manager
      credentialManager.clearCredentialState(ClearCredentialStateRequest())

      Log.d("YkisLog", "$methodName: [FINISH] Полный успех")
      emit(Resource.Success(true))

    } catch (e: FirebaseAuthRecentLoginRequiredException) {
      Log.w("YkisLog", "$methodName: [RE-AUTH REQUIRED] Срок сессии истек")
      auth.signOut()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
      emit(Resource.Error(message = "Для видалення потрібно заново увійти в систему"))

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [CRITICAL ERROR] ${e.message}")
      emit(Resource.Error(message = e.localizedMessage ?: "Помилка видалення"))
    }
  }.flowOn(Dispatchers.IO)

  // В FirebaseService интерфейс и FirebaseServiceImpl
  override suspend fun logoutDirectly() {
    auth.signOut() // Стандартный метод Firebase, который срабатывает мгновенно
  }


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
        Log.e("YkisLog", "FirebaseServiceImpl.addUserFirestore() Error: UID is null")
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

      Log.d("YkisLog", "FirebaseServiceImpl.Firestore Success for $currentUid")

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


      Log.d(
        "YkisLog",
        "FirebaseServiceImpl.addUserFirestore() All databases updated successfully for $currentUid"
      )
      Resource.Success(true)

    } catch (e: Exception) {
      Log.e("YkisLog", "FirebaseServiceImpl.addUserFirestore() Exception: ${e.message}")
      Resource.Error(e.localizedMessage ?: "Process error")
    }
  }

  private suspend fun getSaveUserUidResult(
    uid: String,
    email: String
  ): Resource<GetSimpleResponse> {
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
      Log.e("YkisLog", "FirebaseServiceImpl.reloadFirebaseUser() Error: ${e.message}")
      Resource.Error(message = e.localizedMessage ?: "Помилка оновлення профілю")
    }
  }

  override suspend fun updateUserRoleAndPermissions(
    uid: String,
    addressId: Int?,
    userRole: UserRole,
    osbbId: Int?,
    displayName: String? // Это наша строка "Адрес | Фамилия"
  ) {
    try {
      val updates = mutableMapOf<String, Any>(
        "userRole" to userRole.name,
        "osbbId" to (osbbId ?: FieldValue.delete())
      )

      // 1. Записываем комбинированную строку (Адрес | Фамилия) в displayName
      displayName?.let {
        updates["displayName"] = it
      }

      // 2. Записываем уникальный ID квартиры
      addressId?.let {
        updates["addressId"] = it
      }

      // Выполняем слияние данных. createdAt и другие поля не пострадают.
      db.collection("users")
        .document(uid)
        .set(updates, SetOptions.merge())
        .await()

      Log.d("YkisLog", "FirebaseServiceImpl: Profile updated for $uid. Address: $displayName, AddressID: $addressId")
    } catch (e: Exception) {
      Log.e("YkisLog", "FirebaseServiceImpl ERROR: ${e.message}")
      throw e
    }
  }




  override suspend fun getUserProfile(): UserFirebase = withContext(Dispatchers.IO) {
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid ?: ""

    try {
      // Запрос к документу пользователя в Firestore
      val snapshot = db.collection("users").document(currentUid).get().await()

      // 1. Читаем роль и конвертируем в Enum
      val userRole = snapshot.getString("userRole") ?: "StandardUser"
      // 2. Читаем числовые ID
      val osbbIdFromDb = snapshot.getLong("osbbId")?.toInt() ?: 0
      val addressIdFromDb = snapshot.getLong("addressId")?.toInt() ?: 0

      // 3. Читаем имя (displayName). Если в Firestore пусто — берем из Google Auth
      val displayNameFromDb = snapshot.getString("displayName") ?: currentUser?.displayName

      UserFirebase(
        uid = currentUid,
        email = currentUser?.email ?: "",
        isEmailVerification = currentUser?.isEmailVerified ?: false,
        // Используем имя из БД (там наш Адрес | Фамилия)
        name = displayNameFromDb,
        provider = currentUser?.providerId,
        phone = currentUser?.phoneNumber,
        userRole = userRole,
        osbbId = osbbIdFromDb,
        addressId = addressIdFromDb // Передаем ID квартиры в модель
      )
    } catch (e: Exception) {
      Log.w("YkisLog", "getUserProfile: Document not found or error, using defaults for $currentUid")
      // Если документа еще нет (первый вход)
      UserFirebase(
        uid = currentUid,
        email = currentUser?.email ?: "",
        userRole = UserRole.StandardUser.name,
        osbbId = 0,
        addressId = 0
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
  override suspend fun deleteAccount() {
    val methodName = "FirebaseService.deleteAccount()"
    val user = auth.currentUser
    val uid = user?.uid

    if (uid == null) {
      Log.e("YkisLog", "$methodName: [ERROR] Пользователь не авторизован")
      return
    }

    // 1. Сначала внешняя база (MySQL) - уже работает у тебя
    Log.d("YkisLog", "$methodName: [STEP 1] Удаление из MySQL...")
    // Твой вызов API deleteUserAccount.php

    // 2. Удаляем данные из Firestore (профиль)
    Log.d("YkisLog", "$methodName: [STEP 2] Удаление из Firestore (users/$uid)")
    db.collection("users").document(uid).delete().await()

    // 3. Удаляем данные из Realtime Database
    Log.d("YkisLog", "$methodName: [STEP 3] Удаление из RTDB (users/$uid)")
    chatRepo.realtime.reference.child("users").child(uid).removeValue().await()

    // 4. Удаление из Auth (САМЫЙ ПОСЛЕДНИЙ ШАГ)
    Log.d("YkisLog", "$methodName: [STEP 4] Удаление из Firebase Auth")
    try {
      user.delete().await()
      Log.d("YkisLog", "$methodName: [SUCCESS] Аккаунт полностью удален")
    } catch (e: Exception) {
      if (e.message?.contains("recent-login") == true) {
        Log.w("YkisLog", "$methodName: [WARNING] Требуется свежий логин. Выход...")
        auth.signOut()
      }
      throw e
    }
  }



  override suspend fun firebaseSignUpWithGoogle2(googleCredential: AuthCredential) {
    firebaseSignInWithGoogle(googleCredential)
  }


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
