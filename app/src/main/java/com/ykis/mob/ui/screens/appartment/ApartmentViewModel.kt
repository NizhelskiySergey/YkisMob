/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.ykis.mob.ui.screens.appartment

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.ext.isValidEmail
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.core.NetworkHandler
import com.ykis.mob.domain.UserRole
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.request.AddApartment
import com.ykis.mob.domain.apartment.request.DeleteApartment
import com.ykis.mob.domain.apartment.request.GetApartment
import com.ykis.mob.domain.apartment.request.GetApartmentList
import com.ykis.mob.domain.apartment.request.UpdateBti
import com.ykis.mob.firebase.service.repo.AuthStateResponse
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.screens.bti.ContactUIState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApartmentViewModel(
  private val firebaseService: FirebaseService,
  private val apartmentService: ApartmentService,
  private val logService: LogService

) : BaseViewModel(logService) {

  private val isEmailVerified get() = firebaseService.currentUser?.isEmailVerified ?: false
  val uid get() = firebaseService.uid

  private val displayName get() = firebaseService.displayName
  val email get() = firebaseService.email


  private val _apartment = MutableStateFlow(ApartmentEntity())
  val apartment: StateFlow<ApartmentEntity> get() = _apartment.asStateFlow()

  private val _secretCode = MutableStateFlow("")
  val secretCode: StateFlow<String> = _secretCode.asStateFlow()

  // LaunchScreen
  private val _showError = MutableStateFlow(false)
  val showError: StateFlow<Boolean> = _showError.asStateFlow()

  // Используйте val вместо fun
  val authState: AuthStateResponse = firebaseService.getAuthState(viewModelScope)


  private val _contactUiState = MutableStateFlow(ContactUIState())
  val contactUIState: StateFlow<ContactUIState> = _contactUiState.asStateFlow()

  fun onAppStart(): String {
    val userExists = firebaseService.hasUser
    val emailVerified = firebaseService.isEmailVerified

    // Если пользователь залогинен И почта подтверждена — в приложение.
    // Иначе — на экран входа/регистрации.
    return if (userExists && emailVerified == true) {
      Graph.APARTMENT
    } else {
      Graph.AUTHENTICATION
    }
  }


  /**
   * Инициализирует профиль пользователя при старте приложения.
   * Загружает роль, ID организации и список квартир (для жильцов).
   */
  fun observeUserProfile() {
    viewModelScope.launch {
      Log.d("YkisLog", "ApartmentViewModel observeUserProfile() Loading user profile...")

      // 1. Делаем ОДИН запрос к Firebase (Auth + Firestore)
      val user = withContext(Dispatchers.IO) {
        firebaseService.getUserProfile()
      }

      // 2. Обновляем глобальный стейт атомарно
      _uiState.update { currentState ->
        currentState.copy(
          uid = user.uid,
          email = user.email,
          displayName = user.name,
          userRole = user.userRole,
          osbbId = user.osbbId,

          // Автоматизация: Админов сразу переключаем на их чат (ContentDetail)
          selectedContentDetail = if (user.userRole != UserRole.StandardUser) {
            user.userRole.codeName
          } else {
            currentState.selectedContentDetail
          },

          // Скрываем начальный лоадер приложения
          mainLoading = false
        )
      }

      Log.d("YkisLog", "ApartmentViewModel observeUserProfile() Role loaded: ${user.userRole.name}, OSBB_ID: ${user.osbbId}")

      // 3. Если это жилец — подгружаем список его квартир из PHP бэкенда
      if (user.userRole == UserRole.StandardUser) {
        getApartmentList()
      }
    }
  }





  fun onSecretCodeChange(newValue: String) {
    _secretCode.value = newValue
  }

  fun addApartment(restartApp: () -> Unit) {
    val input = secretCode.value.trim()
    if (input.isEmpty()) return

    val uid = firebaseService.uid ?: return
    val email = firebaseService.email ?: ""

    // РАЗВЕТВЛЕНИЕ: Цифры -> Жилец, Текст -> Админ
    if (input.all { it.isDigit() }) {
      // --- ЛОГИКА ЖИЛЬЦА (Твой текущий код) ---
      apartmentService.addApartment(input, uid, email).onEach { result ->
        handleApartmentResult(result, restartApp)
      }.launchIn(viewModelScope)
    } else {
      // --- ЛОГИКА АДМИНА (Новая) ---
      apartmentService.verifyAdminCode(input, uid).onEach { result ->
        handleAdminResult(result, restartApp)
      }.launchIn(viewModelScope)
    }
  }
  /**
   * Обработка результата добавления квартиры для ОБЫЧНОГО ПОЛЬЗОВАТЕЛЯ (Жильца)
   */
  private fun handleApartmentResult(
    result: Resource<GetSimpleResponse>,
    restartApp: () -> Unit
  ) {
    when (result) {
      is Resource.Success -> {
        // 1. Извлекаем адрес из ответа (предположим, success == 1 пришел из UseCase)
        val newAddressId = result.data?.addressId ?: 0

        // 2. Обновляем локальный стейт текущим ID квартиры
        _uiState.update { currentState ->
          currentState.copy(
            addressId = newAddressId,
            // Принудительно подтверждаем роль, если она была неопределена
            userRole = UserRole.StandardUser
          )
        }

        // 3. Обновляем список всех квартир пользователя (чтобы подтянулись детали)
        // После загрузки списка выполняем финальный переход/рестарт
        getApartmentList {
          setAddressId(newAddressId) // Устанавливаем активную квартиру

          _secretCode.value = "" // Очищаем ввод только после успеха
          SnackbarManager.showMessage(R.string.success_add_flat)

          restartApp() // Вызываем колбэк навигации
        }
      }

      is Resource.Error -> {
        // Выводим ошибку (IncorrectCode, FlatAlreadyInDB и т.д.)
        SnackbarManager.showMessage(result.resourceMessage ?: R.string.error_add_apartment)
      }

      is Resource.Loading -> {
        // Здесь можно включить индикатор загрузки в UI через State
        // _uiState.update { it.copy(isGlobalLoading = true) }
      }
    }
  }

  /**
   * Обработка ответа для АДМИНА
   */
  /**
   * Обработка результата проверки секретного слова для АДМИНИСТРАТОРА.
   * Вызывается, если введенный код содержал буквы.
   */
  private fun handleAdminResult(result: Resource<GetSimpleResponse>, restartApp: () -> Unit) {
    if (result is Resource.Success) {
      val data = result.data ?: return
      val currentUid = firebaseService.uid
      if (currentUid.isEmpty()) return

      viewModelScope.launch {
        try {
          // 1. Сохраняем в Firestore (используем пришедшие из базы значения)
          firebaseService.updateUserRoleAndPermissions(
            uid = currentUid,
            userRole = data.userRole ?: "STANDARD_USER",
            osbbId = data.osbbId
          )

          // 2. Обновляем локальный UI State
          _uiState.update { currentState ->
            val newRole = UserRole.fromString(data.userRole)
            currentState.copy(
              userRole = newRole,
              osbbId = data.osbbId,
              selectedContentDetail = newRole.codeName,
              showDetail = true
            )
          }

          _secretCode.value = ""
          SnackbarManager.showMessage(R.string.admin_access_granted)

          // Перезагружаем приложение для смены графа
          restartApp()

        } catch (e: Exception) {
          logService.logNonFatalCrash(e)
          SnackbarManager.showMessage(R.string.error_admin_auth)
        }
      }
    }
  }






  fun initialContactState() {
    _contactUiState.value = ContactUIState(
      email = _uiState.value.apartment.email,
      phone = _uiState.value.apartment.phone,
      addressId = _uiState.value.addressId,
      address = _uiState.value.address
    )
  }

  fun onEmailChange(newValue: String) {
    _contactUiState.value = _contactUiState.value.copy(email = newValue)
  }

  fun onPhoneChange(newValue: String) {
    _contactUiState.value = _contactUiState.value.copy(phone = newValue)
  }

  fun onUpdateBti(uid: String) {
    // 1. Валидация (остается во ViewModel)
    if (!email.isValidEmail() && email.isNotEmpty()) {
      SnackbarManager.showMessage(R.string.email_error)
      return
    }

    // 2. Вызов через сервис-фасад (убираем прямую зависимость от UseCase)
    apartmentService.updateBti(
      ApartmentEntity(
        addressId = _contactUiState.value.addressId,
        address = _contactUiState.value.address,
        phone = _contactUiState.value.phone,
        email = _contactUiState.value.email,
        uid = uid
      )
    ).onEach { result ->
      when (result) {
        is Resource.Success -> {
          SnackbarManager.showMessage(R.string.updated)
          getApartment() // Обновляем данные
        }
        is Resource.Error -> {
          SnackbarManager.showMessage(result.resourceMessage)
        }
        is Resource.Loading -> {
          // Можно добавить индикатор загрузки, если нужно
        }
      }
    }.launchIn(viewModelScope)
  }


  fun getApartment(addressId: Int = uiState.value.addressId) {
    // Вызов через сервис-фасад (убираем прямую зависимость от UseCase GetApartment)
    apartmentService.getApartment(addressId = addressId, uid = uid ?: "").onEach { result ->
      when (result) {
        is Resource.Success -> {
          Log.d("debug_test1", "success")
          val data = result.data ?: ApartmentEntity()

          // Обновляем основной стейт
          _uiState.value = _uiState.value.copy(
            apartment = data,
            addressId = data.addressId,
            address = data.address,
            houseId = data.houseId,
            osmdId = data.osmdId,
            osbb = data.osbb.toString(),
            apartmentLoading = false,
          )

          // Синхронизируем стейт контактов
          _contactUiState.value = _contactUiState.value.copy(
            addressId = data.addressId,
            email = data.email,
            phone = data.phone,
            address = data.address
          )
        }

        is Resource.Error -> {
          Log.d("debug_test1", "error")
          _uiState.value = _uiState.value.copy(
            error = result.message ?: "Unexpected error!",
            apartmentLoading = false
          )
        }

        is Resource.Loading -> {
          Log.d("debug_test1", "loading")
          _uiState.value = _uiState.value.copy(
            apartmentLoading = true
          )
        }
      }
    }.launchIn(this.viewModelScope)
  }

  fun getApartmentList(onSuccess: () -> Unit = {}) {
    val currentUid = firebaseService.uid
    if (currentUid.isEmpty()) return

    // Вызов через сервис-фасад (вместо прямой зависимости от GetApartmentList)
    apartmentService.getApartmentList(currentUid).onEach { result ->
      _uiState.update { state ->
        when (result) {
          is Resource.Success -> state.copy(
            apartments = result.data ?: emptyList(),
            mainLoading = false
          )
          is Resource.Error -> state.copy(
            error = result.message ?: "Error",
            mainLoading = false
          )
          is Resource.Loading -> state.copy(mainLoading = true)
        }
      }
      // Выполняем onSuccess только при успешном получении данных
      if (result is Resource.Success) onSuccess()
    }.launchIn(viewModelScope)
  }


  fun deleteApartment() {
    // Вызов через сервис-фасад (убираем прямую зависимость от UseCase DeleteApartment)
    apartmentService.deleteApartment(
      addressId = uiState.value.addressId,
      uid = uid ?: ""
    ).onEach { result ->
      when (result) {
        is Resource.Success -> {
          SnackbarManager.showMessage(R.string.success_delete_flat)
          // После успешного удаления обновляем список
          getApartmentList(
            onSuccess = {
              _uiState.update { currentState ->
                currentState.copy(
                  mainLoading = false,
                  addressId = 0
                )
              }
            }
          )
        }

        is Resource.Error -> {
          _uiState.update { currentState ->
            currentState.copy(
              error = result.message ?: "Unexpected error!",
              mainLoading = false
            )
          }
          SnackbarManager.showMessage(result.resourceMessage)
        }

        is Resource.Loading -> {
          _uiState.update { it.copy(mainLoading = true) }
        }
      }
    }.launchIn(this.viewModelScope)
  }


  fun setAddressId(addressId: Int) {
    _uiState.value = uiState.value.copy(
      addressId = addressId
    )
    getApartment(addressId)
  }

}
