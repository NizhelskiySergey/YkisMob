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
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class NewSettingsViewModel(
  private val dataStore: AppSettingsRepository,
  private val application: MainApplication,
  private val clearDatabase: ClearDatabase,
  private val firebaseService: FirebaseService,
  private val chatViewModel: ChatViewModel,
  private val apartmentViewModel: ApartmentViewModel
) : ViewModel() {
  private val _theme = MutableStateFlow<String?>(null)
  val theme = _theme.asStateFlow()
  val displayName get() = firebaseService.displayName
  val photoUrl get() = firebaseService.photoUrl
  val email get() = firebaseService.email

  private val _loading = MutableStateFlow(false)
  val loading = _loading.asStateFlow()

  fun signOut(onSuccess: () -> Unit) {
    val methodName = "NewSettingsViewModel.signOut"
    if (_loading.value) return

    _loading.value = true
    Log.d("YkisLog", "$methodName: [START] Лоадер включен")

    // Используем Dispatchers.Main.immediate, чтобы зайти в launch МГНОВЕННО
    viewModelScope.launch(Dispatchers.Main.immediate + NonCancellable) {
      Log.d("YkisLog", "$methodName: [INSIDE_LAUNCH] Мы внутри корутины!")
      try {
        // 1. Быстрая очистка (память)
        chatViewModel.stopAllTrackers()
        apartmentViewModel.clearState()

        withContext(Dispatchers.IO) {
          // 2. Firebase выход
          firebaseService.logoutDirectly()
          Log.d("YkisLog", "$methodName: [STEP 1] Firebase Logout OK")

          // 3. Очистка Room (с защитой от зависания)
          try {
            withTimeout(2000) {
              clearDatabase().collect { result ->
                if (result is Resource.Success) {
                  Log.d("YkisLog", "$methodName: [STEP 2] База пуста")
                }
              }
            }
          } catch (e: Exception) {
            Log.w("YkisLog", "$methodName: [TIMEOUT] База не ответила, идем дальше")
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL] ${e.message}")
      } finally {
        _loading.value = false
        Log.d("YkisLog", "$methodName: [FINISH] Лоадер OFF, переход...")
        onSuccess()
      }
    }
  }









  // 2. УДАЛЕНИЕ АККАУНТА (Revoke Access)
  fun revokeAccess(onSuccess: () -> Unit) {
    val methodName = "SettingsVM.revokeAccess()"
    Log.d("YkisLog", "$methodName: [START]")

    _loading.value = true
    chatViewModel.stopAllTrackers()
    apartmentViewModel.clearState()

    viewModelScope.launch {
      firebaseService.revokeAccess().collect { result ->
        when (result) {
          is Resource.Loading -> { _loading.value = true }

          is Resource.Success -> {
            Log.d("YkisLog", "$methodName: [SUCCESS] Firebase данные удалены")

            withContext(Dispatchers.IO) {
              Log.d("YkisLog", "$methodName: [CLEANUP] Очистка Room...")
              // Ждем завершения, пока Loading не сменится на Success/Error
              clearDatabase().collect { dbResult ->
                if (dbResult is Resource.Success) {
                  Log.d("YkisLog", "$methodName: [CLEANUP] Room полностью пуста")
                }
              }
            }

            _loading.value = false
            Log.d("YkisLog", "$methodName: [FINISH] Навигация")
            onSuccess()
          }

          is Resource.Error -> {
            Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")

            // 1. Показываем сообщение об ошибке (например, "Нужно перезайти")
            val errorMsg = result.message ?: "Помилка видалення"
            SnackbarManager.showMessage(errorMsg)

            // 2. Выключаем лоадер
            _loading.value = false

            // 3. ДАЕМ ВРЕМЯ ПРОЧИТАТЬ (1.5 - 2 секунды) перед тем как выкинуть на логин
            viewModelScope.launch {
              delay(2000)
              Log.d("YkisLog", "$methodName: [NAVIGATE] После отображения ошибки")
              onSuccess()
            }
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

