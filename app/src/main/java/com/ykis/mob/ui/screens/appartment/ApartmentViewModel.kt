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
import com.ykis.mob.core.Constants.UID
import com.ykis.mob.core.Resource
import com.ykis.mob.core.ext.isValidEmail
import com.ykis.mob.core.snackbar.SnackbarManager
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

//  private val isEmailVerified get() = firebaseService.currentUser?.isEmailVerified ?: false
  val uid get() = firebaseService.uid

//  private val displayName get() = firebaseService.displayName
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
    Log.d("YkisMob", "ApartmentViewModel onAppStart() for userExists: $userExists")
    // Если пользователь залогинен И почта подтверждена — в приложение.
    // Иначе — на экран входа/регистрации.
    return if (userExists && emailVerified == true) {
      Graph.APARTMENT
    } else {
      Graph.AUTHENTICATION
    }
  }


  fun getUserRole() {
    viewModelScope.launch {
      // 1. Получаем типизированный объект User (уже содержит role и orgId)
      val user = withContext(Dispatchers.IO) {
        firebaseService.getUserDocument()
      }

      // 2. Если данные получены, обновляем UI State
      user?.let { userData ->
        // Находим нужный Enum по строковому коду из документа
        val role = UserRole.entries.find { it.codeName == userData.role }
          ?: UserRole.StandardUser
        Log.d("YkisMob", "ApartmentViewModel getUserRole() for role: ${userData.role}")
        Log.d("YkisMob", "ApartmentViewModel getUserRole() for osbbId: ${userData.osbbId}")
        _uiState.update { currentState ->
          currentState.copy(
            userRole = role,
            osbbId = userData.osbbId,
            uid = userData.uid,
            displayName = userData.displayName,
            email = userData.email
          )
        }
      } ?: run {
        // Опционально: обработка случая, если документ пользователя не найден
        Log.e("YkisMob", "getUserRole() User document is null")
      }
    }
  }






  fun onSecretCodeChange(newValue: String) {
    _secretCode.value = newValue
  }

  fun addApartment(restartApp: () -> Unit) {
    // Вызов через сервис-фасад (убираем прямую зависимость от UseCase AddApartment)
    apartmentService.addApartment(
      code = secretCode.value,
      uid = firebaseService.uid,
      email = firebaseService.email
    ).onEach { result ->
      when (result) {
        is Resource.Success -> {
          // Обновляем ID новой квартиры в стейте
          _uiState.update { it.copy(addressId = result.data?.addressId ?: 0) }

          // Обновляем список квартир и выполняем навигацию/рестарт
          getApartmentList {
            setAddressId(_uiState.value.addressId)
            restartApp()
          }

          SnackbarManager.showMessage(R.string.success_add_flat)
          _secretCode.value = ""
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
          Log.d("APART_VIEW_MODEL", "getApartment() success")
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
          Log.d("APART_VIEW_MODEL", " getApartmen() error")
          _uiState.value = _uiState.value.copy(
            error = result.message ?: "Unexpected error!",
            apartmentLoading = false
          )
        }

        is Resource.Loading -> {
          Log.d("APART_VIEW_MODEL", "getApartment() loading")
          _uiState.value = _uiState.value.copy(
            apartmentLoading = true
          )
        }
      }
    }.launchIn(this.viewModelScope)
  }

  fun getApartmentList(onSuccess: () -> Unit = {}) {
    val currentUid = firebaseService.uid
    Log.d("APART_VIEW_MODEL", "getApartmentList() started for UID: $currentUid")

    if (currentUid.isBlank()) {
      Log.d("APART_VIEW_MODEL", "getApartmentList() UID is empty, stopping loading")
      _uiState.update { it.copy(mainLoading = false) }
      return
    }

    apartmentService.getApartmentList(currentUid)
      .onEach { result ->
        Log.d("APART_VIEW_MODEL", "Received result: ${result::class.simpleName}")
        _uiState.update { state ->
          when (result) {
            is Resource.Success -> {
              Log.d("APART_VIEW_MODEL", "getApartmentList() Success! Apartments count: ${result.data?.size}")
              state.copy(apartments = result.data ?: emptyList(), mainLoading = false)
            }
            is Resource.Error -> {
              Log.e("APART_VIEW_MODEL", "getApartmentList() Error: ${result.message}")
              state.copy(mainLoading = false, error = result.message)
            }
            is Resource.Loading -> {
              state.copy(mainLoading = true)
            }
          }
        }
        if (result is Resource.Success) onSuccess()
      }
      .catch { e ->
        Log.e("APART_VIEW_MODEL", "getApartmentList() Flow CRASHED: ${e.message}")
        _uiState.update { it.copy(mainLoading = false, error = e.localizedMessage) }
      }
      .launchIn(viewModelScope)
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

  fun addAdminRole(onSuccess: () -> Unit) {
    val code = _secretCode.value
    if (code.isBlank()) return

    // 1. Используем сервис через UseCase
    apartmentService.verifyAdminCode(
      code = code,
      uid = firebaseService.uid ?: ""
    ).onEach { result ->
      when (result) {
        is Resource.Success -> {
          // 2. Сначала обновляем данные в БД, затем запрашиваем новую роль в стейт
          getUserRole()

          _secretCode.value = ""
          // Убедитесь, что R.string.admin_access_granted добавлен в strings.xml
          SnackbarManager.showMessage(R.string.admin_access_granted)

          // 3. Коллбэк для навигации (например, navController.navigate(Graph.APARTMENT))
          onSuccess()
        }
        is Resource.Error -> {
          SnackbarManager.showMessage(result.resourceMessage)
        }
        is Resource.Loading -> {
          // Можно добавить _uiState.update { it.copy(isLoading = true) }
        }
      }
    }.launchIn(viewModelScope)
  }



  fun setAddressId(addressId: Int) {
    _uiState.value = uiState.value.copy(
      addressId = addressId
    )
    getApartment(addressId)
  }

}
