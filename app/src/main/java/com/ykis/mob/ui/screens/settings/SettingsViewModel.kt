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
    val methodName = "SettingsVM.revokeAccess"
    Log.d("YkisLog", "$methodName: [START]")

    _loading.value = true

    // Используем immediate запуск и NonCancellable, чтобы корутину не "убило" при навигации
    viewModelScope.launch(Dispatchers.Main.immediate + NonCancellable) {
      Log.d("YkisLog", "$methodName: [INSIDE_LAUNCH]")
      try {
        // 1. Предварительная очистка
        chatViewModel.stopAllTrackers()
        apartmentViewModel.clearState()

        // 2. Вызов основного процесса удаления
        firebaseService.revokeAccess().collect { result ->
          when (result) {
            is Resource.Success -> {
              Log.d("YkisLog", "$methodName: [SUCCESS] Облако очищено")

              // 3. Очистка локальной базы Room с таймаутом (чтобы не висеть вечно)
              withContext(Dispatchers.IO) {
                try {
                  withTimeout(2000) {
                    Log.d("YkisLog", "$methodName: [CLEANUP] Запуск Room clean...")
                    clearDatabase().collect { dbResult ->
                      if (dbResult is Resource.Success) {
                        Log.d("YkisLog", "$methodName: [DB_CLEAN] База Room пуста")
                      }
                    }
                  }
                } catch (e: Exception) {
                  Log.w("YkisLog", "$methodName: [TIMEOUT] Room не ответила, идем дальше")
                }
              }

              // Завершаем успех
              _loading.value = false
              Log.d("YkisLog", "$methodName: [FINISH] Успешное удаление. Навигация.")
              onSuccess()
            }

            is Resource.Error -> {
              Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")

              _loading.value = false
              val errorMsg = result.message ?: "Помилка видалення"
              SnackbarManager.showMessage(errorMsg)

              // Даем время прочитать ошибку (напр. Re-auth) и выходим
              delay(2000)
              Log.d("YkisLog", "$methodName: [NAVIGATE] Уход на логин после ошибки")
              onSuccess()
            }

            is Resource.Loading -> {
              Log.d("YkisLog", "$methodName: [LOADING]...")
            }
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
        _loading.value = false
        onSuccess()
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

