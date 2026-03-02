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
  private val getApartmentList: GetApartmentList,
  private val getApartment: GetApartment,
  private val deleteApartment: DeleteApartment,
  private val addApartment: AddApartment,
  private val networkHandler: NetworkHandler,
  private val logService: LogService,
  private val updateBti: UpdateBti
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


  fun observeApartments() {
    viewModelScope.launch { // Стартуем в Main (default для viewModelScope)
      Log.d("uid_null_test", "observeApartments started")

      // 1. Выполняем тяжелые сетевые запросы в IO через async или в самом сервисе
      val userRole = withContext(Dispatchers.IO) { firebaseService.getUserRole() }

      // 2. Получаем данные (синхронные геттеры из сервиса работают быстро)
      val newUid = firebaseService.uid
      val newEmail = firebaseService.email
      val newDisplayName = firebaseService.displayName

      // 3. Обновляем UI стейт строго в Main потоке
      _uiState.update { currentState ->
        currentState.copy(
          uid = newUid,
          displayName = newDisplayName,
          email = newEmail,
          userRole = userRole
        )
      }

      Log.d("iii_test", "Role: ${userRole.codeName}, UID: $newUid")

      // 4. Загружаем список квартир
      getApartmentList()
    }
  }


  fun getUserRole() {
    viewModelScope.launch { // Стартуем в Main (UI) потоке
      // 1. Уходим в IO только для сетевых запросов
      val role = withContext(Dispatchers.IO) { firebaseService.getUserRole() }

      var osbbId: Int? = null
      if (role == UserRole.OsbbUser) {
        osbbId = withContext(Dispatchers.IO) { firebaseService.getOsbbRoleId() }
      }

      // 2. Обновляем State атомарно и в Main потоке
      _uiState.update { currentState ->
        currentState.copy(
          userRole = role,
          osbbRoleId = osbbId,
          // Если UID и другие данные уже есть, их копировать повторно не нужно
          uid = firebaseService.uid,
          displayName = firebaseService.displayName,
          email = firebaseService.email
        )
      }
    }
  }


  fun onSecretCodeChange(newValue: String) {
    _secretCode.value = newValue
  }

  fun addApartment(restartApp: () -> Unit) {
    this.addApartment(
      code = secretCode.value, uid = firebaseService.uid, email = firebaseService.email
    ).onEach { result ->
      when (result) {
        is Resource.Success -> {
          _uiState.value = _uiState.value.copy(
            addressId = result.data!!.addressId
          )
          getApartmentList {
            setAddressId(uiState.value.addressId)
            restartApp()
          }
          SnackbarManager.showMessage(R.string.success_add_flat)
          _secretCode.value = ""
        }

        is Resource.Error -> {
          SnackbarManager.showMessage(result.resourceMessage)
        }

        is Resource.Loading -> {}
      }
    }.launchIn(this.viewModelScope)

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
    if (!email.isValidEmail() && email.isNotEmpty()) {
      SnackbarManager.showMessage(R.string.email_error)
      return
    }
    this.updateBti(
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
          getApartment()
        }

        is Resource.Error -> {
          SnackbarManager.showMessage(result.resourceMessage)
        }

        is Resource.Loading -> {}
      }
    }.launchIn(this.viewModelScope)
  }

  fun getApartment(addressId: Int = uiState.value.addressId) {
    this.getApartment(addressId = addressId, uid).onEach { result ->
      when (result) {
        is Resource.Success -> {
          Log.d("debug_test1", "success")
          this._uiState.value = _uiState.value.copy(
            apartment = result.data ?: ApartmentEntity(),
            addressId = result.data!!.addressId,
            address = result.data.address,
            houseId = result.data.houseId,
            osmdId = result.data.osmdId,
            osbb = result.data.osbb.toString(),
            apartmentLoading = false,
          )
          this._contactUiState.value = _contactUiState.value.copy(
            addressId = result.data.addressId,
            email = result.data.email,
            phone = result.data.phone,
            address = result.data.address
          )
        }

        is Resource.Error -> {
          Log.d("debug_test1", "error")
          this._uiState.value = _uiState.value.copy(
            error = result.message ?: "Unexpected error!",
            apartmentLoading = false
          )
        }

        is Resource.Loading -> {
          Log.d("debug_test1", "loading")
          this._uiState.value = _uiState.value.copy(
            apartmentLoading = true
          )

        }
      }
    }.launchIn(this.viewModelScope)
  }
  fun getApartmentList(onSuccess: () -> Unit = {}) {
    val currentUid = firebaseService.uid
    if (currentUid.isEmpty()) return // Не делаем запрос без UID

    this.getApartmentList(currentUid).onEach { result ->
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
      if (result is Resource.Success) onSuccess()
    }.launchIn(viewModelScope)
  }

  fun deleteApartment(
  ) {
    this.deleteApartment(
      addressId = uiState.value.addressId,
      uid = uid
    ).onEach { result ->
      when (result) {
        is Resource.Success -> {
          SnackbarManager.showMessage(R.string.success_delete_flat)
          getApartmentList(
            onSuccess = {
              this._uiState.value = _uiState.value.copy(
                mainLoading = false,
                addressId = 0
              )
            }
          )
        }

        is Resource.Error -> {
          this._uiState.value = _uiState.value.copy(
            error = result.message ?: "Unexpected error!",
            mainLoading = false
          )
          SnackbarManager.showMessage(result.resourceMessage)
        }

        is Resource.Loading -> {
          _uiState.value = _uiState.value.copy(
            mainLoading = true
          )
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
