package com.ykis.mob.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import com.ykis.mob.MainApplication
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.preferences.AppSettingsRepository
import com.ykis.mob.domain.ClearDatabase
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NewSettingsViewModel(
  private val dataStore: AppSettingsRepository,
  private val application: MainApplication,
  private val clearDatabase: ClearDatabase,
  private val firebaseService: FirebaseService,
  private val chatViewModel: ChatViewModel
) : ViewModel() {
  private val _theme = MutableStateFlow<String?>(null)
  val theme = _theme.asStateFlow()
  val displayName get() = firebaseService.displayName
  val photoUrl get() = firebaseService.photoUrl
  val email get() = firebaseService.email

  private val _loading = MutableStateFlow(false)
  val loading = _loading.asStateFlow()

  // 1. ВЫХОД ИЗ СИСТЕМЫ
  // [СТАБИЛЬНАЯ ВЕРСИЯ]
  // [СТАБИЛЬНАЯ ВЕРСИЯ]
  fun signOut(onSuccess: () -> Unit) {
    val methodName = "SettingsVM.signOut()"
    Log.d("YkisLog", "$methodName: [START]")

    _loading.value = true

    // 1. Остановка всех сетевых процессов чата (пока токен жив)
    chatViewModel.stopAllTrackers()

    viewModelScope.launch {
      try {
        // Шаг 1: Работа с токенами и подписками ПЕРЕД выходом
        val uid = firebaseService.uid
        Log.d("YkisLog", "$methodName: UID перед выходом: $uid")

        withContext(Dispatchers.IO) {
          // Отписываемся от пушей (теперь через Firebase напрямую)
          Firebase.messaging.unsubscribeFromTopic("chat").await()

          // Шаг 2: Физический выход из системы (локально в SDK)
          firebaseService.logoutDirectly()
        }
        Log.d("YkisLog", "$methodName: [SUCCESS] Firebase Auth SignOut")

        // Шаг 3: Очистка локальной базы Room (асинхронно)
        launch(Dispatchers.IO) {
          clearDatabase().firstOrNull()
          Log.d("YkisLog", "$methodName: [CLEANUP] Room DB cleared")
        }

        // Шаг 4: Завершение на главном потоке
        withContext(Dispatchers.Main) {
          _loading.value = false
          Log.d("YkisLog", "$methodName: [FINISH] Вызов onSuccess")
          onSuccess()
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [ERROR] $e")
        _loading.value = false
        onSuccess() // Принудительный выход в любом случае
      }
    }
    // [СТАБИЛЬНАЯ ВЕРСИЯ]


  }

  // 2. УДАЛЕНИЕ АККАУНТА (Revoke Access)
  // [СТАБИЛЬНАЯ ВЕРСИЯ] для NewSettingsViewModel
  fun revokeAccess(onSuccess: () -> Unit) {
    val methodName = "SettingsVM.revokeAccess()"
    Log.d("YkisLog", "$methodName: [START]")

    _loading.value = true
    chatViewModel.stopAllTrackers()

    // КРИТИЧНО: Все вызовы Flow должны быть внутри launch
    viewModelScope.launch {
      firebaseService.revokeAccess().collect { result ->
        when (result) {
          is Resource.Loading -> {
            Log.d("YkisLog", "$methodName: [LOADING]...")
            _loading.value = true
          }
          is Resource.Success -> {
            Log.d("YkisLog", "$methodName: [SUCCESS] Firebase удалил данные")

            // Чистим базу Room перед уходом
            clearDatabase().firstOrNull()

            _loading.value = false
            onSuccess()
          }
          is Resource.Error -> {
            Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")
            _loading.value = false
            // Даже при ошибке (например, RE-AUTH) мы вызываем onSuccess,
            // так как сервис уже сделал signOut()
            onSuccess()
          }
        }
      }
    }
  }




  fun setThemeValue(value: String) {
    viewModelScope.launch {
      dataStore.putThemeStrings(key = "theme", value = value)
      // getThemeValue() здесь больше НЕ нужен
    }
  }

  fun getThemeValue() {
    viewModelScope.launch {
      dataStore.getThemeStrings(key = "theme").collect { value ->
        _theme.value = value
        // Синхронизируем с полем в Application, если это необходимо для легаси-кода
        application.theme.value = value ?: "system"
      }
    }
  }
}

