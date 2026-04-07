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
import com.ykis.mob.domain.UserRole
import com.ykis.mob.domain.UserRole.Companion.fromString
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.firebase.service.repo.AuthStateResponse
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.navigation.InfoApartmentScreenDest
import com.ykis.mob.ui.screens.bti.ContactUIState
import com.ykis.mob.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
  private val chatViewModel: ChatViewModel,
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
      val methodName = "ApartmentViewModel.observeUserProfile()"
      Log.d("YkisLog", "$methodName: [START] Loading user profile...")

      val user = withContext(Dispatchers.IO) {
        firebaseService.getUserProfile()
      }

      // 1. Первичное обновление стейта
      _uiState.update { currentState ->
        currentState.copy(
          uid = user.uid,
          userRole = fromString(user.userRole), // Здесь Enum (для UI)
          osbbId = user.osbbId,
          displayName = user.name ?: "",
          mainLoading = (fromString(user.userRole) == UserRole.StandardUser)
        )
      }

      Log.d("YkisLog", "$methodName: Profile loaded. Role: ${user.userRole}, Name: ${user.name}, OSBB: ${user.osbbId}")

      // --- ЛОГИКА ДЛЯ ЖИЛЬЦА ---
      if (user.userRole == UserRole.StandardUser.name) {
        getApartmentList {
          val currentList = _uiState.value.apartments
          if (currentList.isNotEmpty()) {
            val first = currentList.first()
            val newOsbbId = first.osmdId
            val newAddressId = first.addressId
            val combinedName = "${first.address} | ${first.nanim ?: ""}"

            // 2. Обновляем данные квартиры в стейте
            _uiState.update { currentState ->
              currentState.copy(
                addressId = newAddressId,
                osbbId = newOsbbId,
                osmdId = newOsbbId,
                address = first.address,
                displayName = combinedName,
                apartment = first,
                mainLoading = false
              )
            }

            // 3. ЗАПУСК СЧЕТЧИКОВ И СБОРА ТОКЕНОВ АДМИНОВ
            Log.d("YkisLog", "$methodName: [ACTION] Starting counters for resident ${user.uid}")
            chatViewModel.subscribeToResidentCounters(
              uid = user.uid,
              osbbId = newOsbbId,
              addressId = newAddressId
            )

            // 4. СИНХРОНИЗИРУЕМ FIRESTORE
            if (user.osbbId != newOsbbId || user.name != combinedName) {
              viewModelScope.launch(Dispatchers.IO) {
                try {
                  Log.d("YkisLog", "$methodName: [SYNC] Sending to Firestore: Role=${fromString(user.userRole)}, Name=$combinedName")

                  firebaseService.updateUserRoleAndPermissions(
                    uid = user.uid,
                    addressId = newAddressId,
                    // ИСПРАВЛЕНО: Приводим Enum к String через .name
                    userRole = fromString(user.userRole),
                    osbbId = newOsbbId,
                    displayName = combinedName
                  )
                  Log.d("YkisLog", "$methodName: [SUCCESS] Firestore sync completed")
                } catch (e: Exception) {
                  Log.e("YkisLog", "$methodName: [ERROR] Firestore sync failed: ${e.message}")
                }
              }
            }
          } else {
            Log.w("YkisLog", "$methodName: Apartment list empty")
            _uiState.update { it.copy(mainLoading = false) }
          }
        }
      } else {
        // --- ЛОГИКА ДЛЯ АДМИНА ---
        Log.d("YkisLog", "$methodName: [ADMIN_MODE] Subscribing admin to OSBB ${user.osbbId} chats")
        chatViewModel.trackUserIdentifiersWithRole(fromString(user.userRole), user.osbbId)
        _uiState.update { it.copy(mainLoading = false) }
      }
    }
  }





  fun onSecretCodeChange(newValue: String) {
    _secretCode.value = newValue
  }
  fun addApartment(restartApp: () -> Unit) {

    val input = secretCode.value.trim()
    if (input.isEmpty()) return
    Log.d("YkisLog", "ApartmentViewModel.addApartment ()Button clicked, secret_code: $input")
    val uid = firebaseService.uid ?: return
    val email = firebaseService.email ?: ""

    // РАЗВЕТВЛЕНИЕ: Цифры -> Жилец, Текст -> Админ
    if (input.all { it.isDigit() }) {
      // --- ЛОГИКА ЖИЛЬЦА (Твой текущий код) ---
      apartmentService.addApartment(input, uid, email).onEach { result ->
        handleApartmentResult(uid,result, restartApp)
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
  private suspend fun handleApartmentResult(
    uid: String,
    result: Resource<GetSimpleResponse>,
    restartApp: () -> Unit
  ) {
    val methodName = "ApartmentViewModel.handleApartmentResult()"

    when (result) {
      is Resource.Success -> {
        val data = result.data ?: return
        val newAddressId = data.addressId ?: 0
        val newOsbbId = data.osbbId ?: 0
        val newAddress = data.address ?: ""

        Log.d("YkisLog", "$methodName: [SUCCESS] Начинаем привязку квартиры: $newAddress (ID: $newAddressId)")

        try {
          // 1. СИНХРОНИЗАЦИЯ С FIREBASE (Передаем роль как String)
          Log.d("YkisLog", "$methodName: [STEP 1] Запись в Firestore. Роль: ${UserRole.StandardUser.name}")
          firebaseService.updateUserRoleAndPermissions(
            uid = uid,
            addressId = newAddressId,
            userRole = UserRole.StandardUser, // ПЕРЕДАЕМ СТРОКУ
            osbbId = newOsbbId,
            displayName = newAddress
          )

          // 2. ОБНОВЛЕНИЕ ЛОКАЛЬНОГО UI STATE
          Log.d("YkisLog", "$methodName: [STEP 2] Обновление локального UI State")
          _uiState.update { currentState ->
            currentState.copy(
              addressId = newAddressId,
              osmdId = newOsbbId,
              osbbId = newOsbbId,
              address = newAddress,
              userRole = UserRole.StandardUser, // Здесь Enum
              mainLoading = false,
              apartmentLoading = false
            )
          }

          // 3. ПОЛУЧЕНИЕ СПИСКА И ПОДПИСКА НА ЧАТЫ
          getApartmentList {
            Log.d("YkisLog", "$methodName: [STEP 3] Список квартир обновлен. Запуск счетчиков чатов.")

            // Активируем Badge для новой квартиры
            chatViewModel.subscribeToResidentCounters(
              uid = uid,
              osbbId = newOsbbId,
              addressId = newAddressId
            )

            _secretCode.value = ""
            SnackbarManager.showMessage(R.string.success_add_flat)

            viewModelScope.launch {
              Log.d("YkisLog", "$methodName: [FINISH] Перезапуск приложения через 100мс")
              delay(100)
              restartApp()
            }
          }

        } catch (e: Exception) {
          Log.e("YkisLog", "$methodName: [CRITICAL ERROR] Ошибка при синхронизации: ${e.message}", e)
          SnackbarManager.showMessage(R.string.error_add_apartment)
          _uiState.update { it.copy(mainLoading = false) }
        }
      }

      is Resource.Error -> {
        Log.e("YkisLog", "$methodName: [API ERROR] ${result.resourceMessage}")
        _uiState.update { it.copy(mainLoading = false) }
        SnackbarManager.showMessage(result.resourceMessage ?: R.string.error_add_apartment)
      }

      is Resource.Loading -> {
        Log.d("YkisLog", "$methodName: [LOADING] Обработка запроса...")
        _uiState.update { it.copy(mainLoading = true) }
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
    val methodName = "ApartmentViewModel.handleAdminResult()"

    when (result) {
      is Resource.Success -> {
        val data = result.data ?: return
        val currentUid = firebaseService.uid ?: ""
        if (currentUid.isEmpty()) {
          Log.e("YkisLog", "$methodName ERROR: Firebase UID is empty")
          return
        }

        val mappedRole = UserRole.fromString(data.userRole)
        val newOsbbId = data.osbbId

        // ПРЕДОХРАНИТЕЛЬ: Если роль и подразделение уже те же — просто переходим
        if (_uiState.value.userRole == mappedRole && _uiState.value.osbbId == newOsbbId) {
          Log.d("YkisLog", "$methodName: Role already synced. Navigating...")
          restartApp()
          return
        }

        viewModelScope.launch {
          try {
            Log.d("YkisLog", "$methodName SUCCESS: MappedRole=${mappedRole.name}, OSBB_ID=$newOsbbId")

            // 2. В Firestore записываем данные (для админа адрес всегда null)
            firebaseService.updateUserRoleAndPermissions(
              uid = currentUid,
              addressId = 0,
              userRole = mappedRole,
              osbbId = newOsbbId,
              displayName = null // ГАРАНТИРУЕМ отсутствие адреса для админа
            )

            // 3. Обновляем локальный стейт
            _uiState.update { currentState ->
              currentState.copy(
                userRole = mappedRole,
                osbbId = newOsbbId,
                osmdId = newOsbbId,
                addressId = 0,
                address = "", // Очищаем старый адрес жильца, если он был
                selectedContentDetail = mappedRole.codeName,
                mainLoading = false
              )
            }

            _secretCode.value = ""
            Log.d("YkisLog", "$methodName: State updated, navigating to first screen.")

            // Даем небольшую задержку, чтобы Firestore успел прописать права
            // и NavHost не "захлебнулся" при пересоздании экрана
            delay(150)
            restartApp()

          } catch (e: Exception) {
            Log.e("YkisLog", "$methodName CRASH: ${e.message}")
            _uiState.update { it.copy(mainLoading = false) }
            SnackbarManager.showMessage(R.string.error_admin_auth)
          }
        }
      }
      is Resource.Error -> {
        Log.w("YkisLog", "$methodName ERROR: ${result.message}")
        _uiState.update { it.copy(mainLoading = false) }
        SnackbarManager.showMessage(result.resourceMessage ?: R.string.error_invalid_admin_code)
      }
      is Resource.Loading -> {
        _uiState.update { it.copy(mainLoading = true) }
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
    // 1. ПРОВЕРКА: Если мы уже загрузили этот ID и не находимся в процессе загрузки - выходим
    if (addressId == uiState.value.apartment.addressId && !uiState.value.apartmentLoading) {
      return
    }

    if (addressId <= 0) return

    val currentUid = uid ?: ""

    apartmentService.getApartment(addressId = addressId, uid = currentUid).onEach { result ->
      when (result) {
        is Resource.Success -> {
          val data = result.data ?: ApartmentEntity()
          Log.d("YkisLog", "ApartmentViewModel.getApartment($addressId) -> SUCCESS")

          // 2. АТОМАРНОЕ ОБНОВЛЕНИЕ ЧЕРЕЗ update
          _uiState.update { currentState ->
            currentState.copy(
              apartment = data,
              addressId = data.addressId,
              address = data.address,
              houseId = data.houseId,
              osmdId = data.osmdId,
              osbbId = data.osmdId, // Синхронизируем наш новый ключ
              osbb = data.osbb.toString(),
              apartmentLoading = false,
            )
          }

          // Синхронизируем стейт контактов
          _contactUiState.update { currentContact ->
            currentContact.copy(
              addressId = data.addressId,
              email = data.email,
              phone = data.phone,
              address = data.address
            )
          }
        }

        is Resource.Error -> {
          Log.e("YkisLog", "ApartmentViewModel.getApartment($addressId) -> ERROR: ${result.message}")
          _uiState.update { it.copy(
            error = result.message ?: "Unexpected error!",
            apartmentLoading = false
          )}
        }

        is Resource.Loading -> {
          Log.d("YkisLog", "ApartmentViewModel.getApartment($addressId) -> LOADING")
          _uiState.update { it.copy(apartmentLoading = true) }
        }
      }
    }.launchIn(this.viewModelScope)
  }


  fun getApartmentList(onSuccess: () -> Unit = {}) {
    val currentUid = firebaseService.uid ?: ""
    if (currentUid.isEmpty()) return

    apartmentService.getApartmentList(currentUid).onEach { result ->
      _uiState.update { state ->
        when (result) {
          is Resource.Success -> {
            val newList = result.data ?: emptyList()
            // Если сейчас ничего не выбрано (ID=0), берем данные первой квартиры
            if (state.addressId == 0 && newList.isNotEmpty()) {
              val first = newList.first()
              state.copy(
                apartments = newList,
                addressId = first.addressId,
                address = first.address,
                osbb = first.osbb.toString(), // Оживляем кнопку ОСББ
                osbbId = first.osmdId,
                apartment = first,
                mainLoading = false
              )
            } else {
              // Если ID уже есть, просто обновляем список (твой старый код)
              state.copy(
                apartments = newList,
                mainLoading = false
              )
            }
          }
          is Resource.Error -> state.copy(error = result.message ?: "Error", mainLoading = false)
          is Resource.Loading -> state.copy(mainLoading = true)
        }
      }
      if (result is Resource.Success) onSuccess()
    }.launchIn(viewModelScope)
  }


  fun deleteApartment(onNavigate: (String) -> Unit) {
    val currentAddressId = _uiState.value.addressId
    val currentUid = firebaseService.uid ?: ""

    apartmentService.deleteApartment(
      addressId = currentAddressId,
      uid = currentUid
    ).onEach { result ->
      when (result) {
        is Resource.Success -> {
          SnackbarManager.showMessage(R.string.success_delete_flat)

          // 1. Обновляем список квартир из сети
          getApartmentList {
            val updatedList = _uiState.value.apartments

            if (updatedList.isEmpty()) {
              // 2. Квартир нет — сбрасываем стейт и уходим на AddApartment
              _uiState.update { it.copy(
                addressId = 0,
                address = "",
                apartment = ApartmentEntity()
              ) }
              onNavigate(AddApartmentScreen.route)
            } else {
              // 3. Квартиры остались — выбираем первую из списка.
              // Мы НЕ вызываем onNavigate, чтобы не пересоздавать экран и не ломать Drawer.
              // Экран обновится сам, так как setAddressId изменит UIState.
              val nextApartment = updatedList.first()
              setAddressId(nextApartment.addressId)
            }
          }
        }

        is Resource.Error -> {
          _uiState.update { it.copy(mainLoading = false) }
          SnackbarManager.showMessage(result.resourceMessage ?: R.string.error_delete_flat)
        }

        is Resource.Loading -> {
          _uiState.update { it.copy(mainLoading = true) }
        }
      }
    }.launchIn(this.viewModelScope)
  }
  fun setAddressId(id: Int) {
    val selected = _uiState.value.apartments.find { it.addressId == id } ?: return
    val currentUid = firebaseService.uid ?: ""

    // Формируем заголовок "Адрес | Фамилия"
    val combinedName = "${selected.address} | ${selected.nanim ?: ""}"

    // Предохранитель от лишних обновлений
    if (_uiState.value.addressId == id && _uiState.value.displayName == combinedName) return

    _uiState.update { it.copy(
      addressId = id,
      address = selected.address,
      displayName = combinedName,
      osbbId = selected.osmdId,
      osmdId = selected.osmdId
    )}

    if (currentUid.isNotEmpty() && _uiState.value.userRole == UserRole.StandardUser) {
      viewModelScope.launch(Dispatchers.IO) {
        firebaseService.updateUserRoleAndPermissions(
          uid = currentUid,
          userRole = UserRole.StandardUser,
          osbbId = selected.osmdId,
          addressId = id,           // Передаем AddressID
          displayName = combinedName // Передаем сформированную строку
        )
      }
    }
    getApartment(id)
  }












}
