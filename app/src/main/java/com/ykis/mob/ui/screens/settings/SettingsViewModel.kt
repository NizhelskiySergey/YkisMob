package com.ykis.mob.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykis.mob.MainApplication
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.preferences.AppSettingsRepository
import com.ykis.mob.domain.ClearDatabase
import com.ykis.mob.firebase.messaging.removeFcmTokenOnLogout
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class NewSettingsViewModel(
  private val dataStore: AppSettingsRepository,
  private val application: MainApplication,
  private val clearDatabase: ClearDatabase,
  private val firebaseService: FirebaseService
//  private val chatViewModel: ChatViewModel,
//  private val apartmentViewModel: ApartmentViewModel
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
    Log.d("YkisLog", "$methodName: [START]")

    viewModelScope.launch(Dispatchers.Main.immediate + NonCancellable) {
      try {
        // --- КРИТИЧЕСКИЙ ШАГ: Удаление токена ДО выхода из Firebase ---
        // Мы делаем это первым делом, пока Auth.currentUser еще валиден
        val currentUid = firebaseService.getUid() // Убедись, что в сервисе есть этот метод
        if (currentUid != null) {
          try {
            // Используем withTimeout, чтобы удаление токена не вешало выход навсегда
            withTimeout(3000) {

              // Вызываем твой метод удаления (его нужно обернуть в suspend или использовать await)
              removeFcmTokenOnLogout(currentUid)
              Log.d("YkisLog", "$methodName: [TOKEN] Запрос на удаление токена отправлен")
            }
          } catch (e: Exception) {
            Log.w("YkisLog", "$methodName: [TOKEN_ERR] Не удалось удалить токен, продолжаем выход...")
          }
        }

        // 1. Остановка всех активных процессов Firebase
        firebaseService.stopAllListeners()

        withContext(Dispatchers.IO) {
          // 2. Выход из Firebase (теперь безопасно закрываем сессию)
          firebaseService.logoutDirectly()
          Log.d("YkisLog", "$methodName: [STEP 1] Firebase Logout OK")

          // 3. Сброс согласия (DataStore/Prefs)
          firebaseService.setAgreement(false)

          // 4. Очистка локальной базы Room
          try {
            withTimeout(2000) {
              clearDatabase().collect { result ->
                if (result is Resource.Success) {
                  Log.d("YkisLog", "$methodName: [STEP 2] База Room очищена")
                }
              }
            }
          } catch (e: Exception) {
            Log.w("YkisLog", "$methodName: [TIMEOUT] Очистка БД затянулась")
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL ERROR] ${e.message}")
      } finally {
        _loading.value = false
        Log.d("YkisLog", "$methodName: [FINISH] Переход на экран входа")
        onSuccess()
      }
    }
  }



  // 2. УДАЛЕНИЕ АККАУНТА (Revoke Access)
  fun revokeAccess(onSuccess: () -> Unit) {
    val methodName = "SettingsVM.revokeAccess"
    Log.d("YkisLog", "$methodName: [START]")

    if (_loading.value) return
    _loading.value = true

    // NonCancellable гарантирует завершение очистки даже при закрытии экрана
    viewModelScope.launch(Dispatchers.Main.immediate + NonCancellable) {
      Log.d("YkisLog", "$methodName: [INSIDE_LAUNCH]")
      try {
        // 1. Остановка всех активных слушателей Firebase напрямую через сервис
        // Это заменяет chatViewModel.stopAllTrackers() и apartmentViewModel.clearState()
        firebaseService.stopAllListeners()
        Log.d("YkisLog", "$methodName: [STEP 1] Все слушатели Firebase остановлены")

        // 2. Процесс удаления данных из облака (Firestore + Auth)
        firebaseService.revokeAccess().collect { result ->
          when (result) {
            is Resource.Success -> {
              Log.d("YkisLog", "$methodName: [STEP 2] Облако очищено (Firestore/Auth)")

              withContext(Dispatchers.IO) {
                // 3. Сброс согласия (DataStore)
                firebaseService.setAgreement(false)
                Log.d("YkisLog", "$methodName: [STEP 3] Согласие сброшено")

                // 4. Очистка локальной БД Room
                try {
                  withTimeout(2500) {
                    Log.d("YkisLog", "$methodName: [STEP 4] Запуск очистки Room...")
                    clearDatabase().collect { dbResult ->
                      if (dbResult is Resource.Success) {
                        Log.d("YkisLog", "$methodName: [DB_CLEAN] Локальная база пуста")
                      }
                    }
                  }
                } catch (e: Exception) {
                  Log.w("YkisLog", "$methodName: [TIMEOUT] Очистка БД пропущена по времени")
                }
              }

              _loading.value = false
              Log.d("YkisLog", "$methodName: [FINISH] Полный успех. Уходим на экран входа.")
              onSuccess()
            }

            is Resource.Error -> {
              Log.e("YkisLog", "$methodName: [ERROR] Ошибка удаления: ${result.message}")
              _loading.value = false
              SnackbarManager.showMessage(result.message ?: "Помилка видалення аккаунта")

              // В случае ошибки всё равно выходим через паузу, чтобы не висеть в битом стейте
              delay(2000)
              onSuccess()
            }

            is Resource.Loading -> {
              Log.d("YkisLog", "$methodName: [LOADING] Удаление данных...")
            }
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL_ERROR] Критический сбой: ${e.message}")
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

