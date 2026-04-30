package com.ykis.mob.firebase.service.impl

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.remoteconfig.get
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.entity.UserFirebase
import com.ykis.mob.firebase.service.repo.AuthStateResponse
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.OneTapSignInResponse
import com.ykis.mob.firebase.service.repo.ReloadUserResponse
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
import com.ykis.mob.BuildConfig
class FirebaseServiceImpl(
  private val context: Context,
  private val auth: FirebaseAuth,
  private val db: FirebaseFirestore,
  private val apartmentService: ApartmentService,
  private val chatRepo: ChatRepository
) : FirebaseService {
  private val credentialManager by lazy { CredentialManager.create(context) }
  private val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
  private val remoteConfig get() = Firebase.remoteConfig
  // Список для слушателей Firestore
  private val snapshotListeners = mutableListOf<ListenerRegistration>()

  // Карта для слушателей Realtime Database (чтобы знать, какой откуда удалять)
  private val dbListeners = mutableMapOf<DatabaseReference, ValueEventListener>()

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

  // --- Remote Config ---
  override val isWiFiCheckConfig: Boolean get() = remoteConfig["loading_from_wifi"].asBoolean()
  override val isMobileCheckConfig: Boolean get() = remoteConfig["loading_from_mobile"].asBoolean()
  override val agreementTitle: String get() = remoteConfig["agreement_title"].asString()
  override val agreementText: String get() = remoteConfig["agreement_text"].asString()

  init {
    if (BuildConfig.DEBUG) {
      val configSettings = remoteConfigSettings { minimumFetchIntervalInSeconds = 0 }
      remoteConfig.setConfigSettingsAsync(configSettings)
    }
    remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
  }

  override suspend fun fetchConfiguration(): Boolean = try {
    remoteConfig.fetchAndActivate().await()
  } catch (e: Exception) { false }

  override suspend fun isUserAgreed(): Boolean = sharedPrefs.getBoolean("is_agreed", false)
  override suspend fun setAgreement(agreed: Boolean) { sharedPrefs.edit { putBoolean("is_agreed", agreed) } }

  // --- Авторизация ---
  override suspend fun firebaseSignInWithEmailAndPassword(email: String, password: String) {
    withContext(Dispatchers.IO) { auth.signInWithEmailAndPassword(email, password).await() }
  }

  override suspend fun firebaseSignInWithGoogle(googleCredential: AuthCredential): SignInWithGoogleResponse = try {
    withContext(Dispatchers.IO) {
      auth.signInWithCredential(googleCredential).await()
      Resource.Success(true)
    }
  } catch (e: Exception) { Resource.Error(e.localizedMessage ?: "Google Auth Failed") }

  // --- Синхронизация профиля и ролей ---
  override suspend fun addUserFirestore(): addUserFirestoreResponse = withContext(Dispatchers.IO) {
    val methodName = "FirebaseServiceImpl.addUserFirestore"
    try {
      val currentUser = auth.currentUser
      val currentUid = currentUser?.uid
      val userEmail = currentUser?.email ?: ""

      if (currentUid.isNullOrEmpty()) {
        Log.e("YkisLog", "$methodName: [ERROR] UID is null")
        return@withContext Resource.Error<Boolean>("UID is empty")
      }

      Log.d("YkisLog", "$methodName: [START] UID: $currentUid")

      // 1. ПРОВЕРКА СУЩЕСТВУЮЩЕГО ПРОФИЛЯ
      val userDoc = db.collection("users").document(currentUid).get().await()
      val isNewUser = !userDoc.exists()

      // 2. ПОДГОТОВКА ДАННЫХ
      val userMap: HashMap<String, Any> = hashMapOf(
        "uid" to currentUid,
        "email" to userEmail,
        "displayName" to (currentUser.displayName ?: ""),
        "lastLogin" to com.google.firebase.Timestamp.now()
      )

      if (isNewUser) {
        Log.d("YkisLog", "$methodName: [NEW_USER] Регистрация...")
        userMap["userRole"] = "STANDARD_USER"
        userMap["osbbId"] = 0
        userMap["createdAt"] = com.google.firebase.Timestamp.now()
      } else {
        val currentOsbbId = userDoc.getLong("osbbId") ?: 0
        Log.d("YkisLog", "$methodName: [EXISTING_USER] osbbId в облаке: $currentOsbbId")
      }

      // 3. ЗАПИСЬ В FIRESTORE (Основной приоритет)
      db.collection("users")
        .document(currentUid)
        .set(userMap, SetOptions.merge())
        .await()

      Log.d("YkisLog", "$methodName: [SUCCESS] Firestore обновлен")

      // 4. МЯГКАЯ СИНХРОНИЗАЦИЯ С MySQL
      // Мы не прерываем вход, если внешняя БД вернула ошибку (null или UserUIdExist)
      try {
        val result = getSaveUserUidResult(currentUid, userEmail)
        if (result is Resource.Error) {
          Log.w("YkisLog", "$methodName: [EXTERNAL_DB_WARNING] MySQL ошибка: ${result.message}")
        } else {
          Log.d("YkisLog", "$methodName: [EXTERNAL_DB_SUCCESS] MySQL обновлен")
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [EXTERNAL_DB_EXCEPTION] ${e.message}")
      }

      // 5. ГАРАНТИРУЕМ УСПЕХ
      Log.d("YkisLog", "$methodName: [FINISH] Профиль готов")
      Resource.Success(true)

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      Resource.Error<Boolean>(e.localizedMessage ?: "Process error")
    }
  }


  override suspend fun updateUserRoleAndPermissions(
    uid: String,
    addressId: Int?,
    userRole: UserRole,
    osbbId: Int?,
    displayName: String?
  ) {
    val methodName = "FirebaseServiceImpl.updateUserRoleAndPermissions"

    // 1. УМНЫЙ ПРЕДОХРАНИТЕЛЬ
    // Блокируем запись osbbId=0 ТОЛЬКО если роль — админ конкретного дома (OsbbUser)
    // Для VodokanalUser, TboUser и др. osbbId=0 — это норма (работа по всему городу)
//    if (userRole == UserRole.OsbbUser && osbbId == 0) {
//      Log.w("YkisLog", "$methodName: [PROTECT] Запись заблокирована: OsbbUser не может иметь osbbId=0")
//      return
//    }

    try {
      Log.d("YkisLog", "$methodName: [START] UID: $uid, Role: $userRole, osbbId: $osbbId, Name: $displayName")

      // 2. ПОДГОТОВКА ДАННЫХ
      val updates = mutableMapOf<String, Any>(
        "userRole" to userRole.name,
        // Если osbbId null, используем 0 как дефолт для организаций
        "osbbId" to (osbbId ?: 0)
      )

      displayName?.let { updates["displayName"] = it }
      addressId?.let { updates["addressId"] = it }

      // 3. ЗАПИСЬ В FIRESTORE
      db.collection("users")
        .document(uid)
        .set(updates, SetOptions.merge())
        .await()

      Log.d("YkisLog", "$methodName: [SUCCESS] Профиль обновлен в облаке для $uid")

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [ERROR] Ошибка Firestore: ${e.message}")
    }
  }


  override suspend fun getUserProfile(): UserFirebase = withContext(Dispatchers.IO) {
    try {
      val snapshot = db.collection("users").document(uid).get().await()
      val userRole = snapshot.getString("userRole") ?: UserRole.StandardUser.name
      val osbbId = snapshot.getLong("osbbId")?.toInt() ?: 0
      val addressId = snapshot.getLong("addressId")?.toInt() ?: 0
      val displayNameFromDb = snapshot.getString("displayName") ?: auth.currentUser?.displayName

      UserFirebase(
        uid = uid,
        email = auth.currentUser?.email ?: "",
        isEmailVerification = auth.currentUser?.isEmailVerified ?: false,
        name = displayNameFromDb,
        userRole = userRole,
        osbbId = osbbId,
        addressId = addressId
      )
    } catch (e: Exception) {
      UserFirebase(uid = uid, email = email, userRole = UserRole.StandardUser.name)
    }
  }

  // --- Удаление и Выход ---
  override fun revokeAccess(): Flow<Resource<Boolean>> = flow {
    val methodName = "FirebaseServiceImpl.revokeAccess"
    emit(Resource.Loading())
    try {
      val user = auth.currentUser ?: throw Exception("Auth session expired")
      val currentUid = user.uid

      // 1. MySQL (через UseCase/Service)
      apartmentService.deleteUserAccount(currentUid, user.email ?: "").filter { it !is Resource.Loading }.first()

      // 2. Очистка Storage, RTDB, Firestore
      try { chatRepo.storage.reference.child("avatars/$currentUid").delete().await() } catch (e: Exception) {}
      try { chatRepo.realtime.getReference("users").child(currentUid).removeValue().await() } catch (e: Exception) {}
      db.collection("users").document(currentUid).delete().await()

      // 3. Auth
      user.delete().await()
      credentialManager.clearCredentialState(ClearCredentialStateRequest())

      emit(Resource.Success(true))
    } catch (e: Exception) { emit(Resource.Error(e.localizedMessage)) }
  }.flowOn(Dispatchers.IO)

  override fun getAuthState(viewModelScope: CoroutineScope): AuthStateResponse {
    return callbackFlow {
      val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser != null) }
      auth.addAuthStateListener(listener)
      awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), auth.currentUser != null)
  }

  // Вспомогательные методы
  private suspend fun getSaveUserUidResult(uid: String, email: String) =
    apartmentService.saveUserUid(uid, email).filter { it !is Resource.Loading }.first()

  override suspend fun logoutDirectly() { auth.signOut() }
  override fun signOut() = flow {
    auth.signOut()
    credentialManager.clearCredentialState(ClearCredentialStateRequest())
    emit(Resource.Success(true))
  }
  override suspend fun getUid() = uid
  override suspend fun getEmail() = email
  override suspend fun getDisplayName() = displayName

  override fun stopAllListeners() {
    val methodName = "FirebaseService.stopAllListeners"
    Log.d("YkisLog", "$methodName: [START] Очистка всех активных соединений")

    try {
      // 1. Очистка Firestore слушателей (профиль, настройки и т.д.)
      snapshotListeners.forEach { it.remove() }
      snapshotListeners.clear()
      Log.d("YkisLog", "$methodName: Firestore слушатели удалены")

      // 2. Очистка Realtime Database (чаты, счетчики непрочитанных)
      dbListeners.forEach { (ref, listener) ->
        ref.removeEventListener(listener)
      }
      dbListeners.clear()
      Log.d("YkisLog", "$methodName: Realtime Database слушатели удалены")

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
    }
  }


  override suspend fun reloadFirebaseUser(): ReloadUserResponse = try { auth.currentUser?.reload()?.await(); Resource.Success(true) } catch (e: Exception) { Resource.Error(e.message) }
// --- Реализация пропущенных методов ---

  override suspend fun authenticate(email: String, password: String) {
    val methodName = "FirebaseService.authenticate"
    try {
      auth.signInWithEmailAndPassword(email, password).await()
      Log.d("YkisLog", "$methodName: [SUCCESS] Вход выполнен")
    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      throw e
    }
  }

  override suspend fun sendRecoveryEmail(email: String) {
    auth.sendPasswordResetEmail(email).await()
  }

  override suspend fun linkAccount(email: String, password: String) {
    val credential = EmailAuthProvider.getCredential(email, password)
    auth.currentUser?.linkWithCredential(credential)?.await()
  }

  // Метод удаления аккаунта (базовая обертка над существующей логикой)
  override suspend fun deleteAccount() {
    val methodName = "FirebaseService.deleteAccount"
    try {
      // Здесь можно вызвать твой revokeAccess().first(), чтобы пройти все круги очистки
      auth.currentUser?.delete()?.await()
      Log.d("YkisLog", "$methodName: [SUCCESS] Аккаунт удален")
    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      throw e
    }
  }

  override suspend fun oneTapSignInWithGoogle(context: Context): OneTapSignInResponse = try {
    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(false)
      .setServerClientId(context.getString(R.string.web_client_id))
      .setAutoSelectEnabled(true)
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    val result = credentialManager.getCredential(context = context, request = request)
    Resource.Success(result)
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Google Auth Error")
  }

  override suspend fun firebaseSignUpWithEmailAndPassword(email: String, password: String): SignUpResponse = try {
    auth.createUserWithEmailAndPassword(email, password).await()
    addUserFirestore() // Сразу создаем профиль
    Resource.Success(true)
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Registration Failed")
  }

  override suspend fun sendEmailVerification(): Resource<Boolean> = try {
    auth.currentUser?.sendEmailVerification()?.await()
    Resource.Success(true)
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Verification Error")
  }

  override suspend fun sendPasswordResetEmail(email: String): Resource<Boolean> = try {
    auth.sendPasswordResetEmail(email).await()
    Resource.Success(true)
  } catch (e: Exception) {
    Resource.Error(e.localizedMessage ?: "Reset Error")
  }

  override fun getProvider(viewModelScope: CoroutineScope): String {
    return auth.currentUser?.providerData?.getOrNull(1)?.providerId ?: "password"
  }

  override fun revokeAccessEmail(): Flow<Resource<Boolean>> = revokeAccess() // Используем общую логику удаления

}
