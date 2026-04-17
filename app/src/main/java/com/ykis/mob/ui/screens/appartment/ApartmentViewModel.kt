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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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

  private var lastLoadedAddressId: Int = -1

  private val _apartment = MutableStateFlow(ApartmentEntity())
  val apartment: StateFlow<ApartmentEntity> get() = _apartment.asStateFlow()

  private val _secretCode = MutableStateFlow("")
  val secretCode: StateFlow<String> = _secretCode.asStateFlow()


  fun onSecretCodeChanged(newCode: String) {
    _secretCode.value = newCode
  }

  // LaunchScreen
  private val _showError = MutableStateFlow(false)
  val showError: StateFlow<Boolean> = _showError.asStateFlow()

  // Используйте val вместо fun
  val authState: AuthStateResponse = firebaseService.getAuthState(viewModelScope)


  private val _contactUiState = MutableStateFlow(ContactUIState())
  val contactUIState: StateFlow<ContactUIState> = _contactUiState.asStateFlow()
  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  // Список, который видит UI (отфильтрованный)
  val filteredApartments = combine(_searchQuery, _uiState) { query, state ->
    if (query.isEmpty()) {
      state.apartments
    } else {
      state.apartments.filter {
        it.address.contains(query, ignoreCase = true) ||
          (it.nanim?.contains(query, ignoreCase = true) ?: false)
      }
    }
  }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
  fun onSearchQueryChanged(newQuery: String) {
    _searchQuery.value = newQuery
  }
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

      // 1. ПРОВЕРКА АВТОРИЗАЦИИ
      val currentUser = firebaseService.currentUser
      if (currentUser == null) {
        Log.d("YkisLog", "$methodName: [STOP] Пользователь не авторизован.")
        _uiState.update { it.copy(mainLoading = false) }
        return@launch
      }

      Log.d("YkisLog", "$methodName: [START] Запуск для UID: ${currentUser.uid}")

      // УМНЫЙ ЛОАДЕР: Включаем крутилку только если список квартир пуст
      if (_uiState.value.apartments.isEmpty()) {
        _uiState.update { it.copy(mainLoading = true) }
      }

      // 2. ПОЛУЧЕНИЕ ПРОФИЛЯ
      val user = withContext(Dispatchers.IO) { firebaseService.getUserProfile() }

      if (firebaseService.currentUser == null) {
        Log.d("YkisLog", "$methodName: [ABORT] Сессия закрыта во время запроса")
        _uiState.update { it.copy(mainLoading = false) }
        return@launch
      }

      val currentUserRole = UserRole.fromString(user.userRole)
      _uiState.update { it.copy(
        uid = user.uid,
        userRole = currentUserRole,
        osbbId = user.osbbId,
        displayName = user.name ?: "",
      )}

      // 3. ЛОГИКА ЖИЛЬЦА
      if (currentUserRole == UserRole.StandardUser) {
        apartmentService.getApartmentList(user.uid).collect { result ->
          if (firebaseService.currentUser == null) return@collect

          when (result) {
            is Resource.Success -> {
              val apartments = result.data ?: emptyList()
              Log.d("YkisLog", "$methodName: [FETCH] Квартир получено: ${apartments.size}")

              if (apartments.isNotEmpty()) {
                val currentSelectedId = _uiState.value.addressId

                // Сохраняем текущий выбор или берем первую
                val target = apartments.find { it.addressId == currentSelectedId }
                  ?: apartments.first()

                val combinedName = "${target.address} | ${target.nanim ?: ""}"

                Log.d("YkisLog", "$methodName: [TARGET] Выбран ID: ${target.addressId}")

                _uiState.update { it.copy(
                  apartments = apartments,
                  addressId = target.addressId,
                  osbbId = target.osmdId,
                  address = target.address,
                  displayName = combinedName,
                  mainLoading = false // ВЫКЛЮЧАЕМ лоадер
                )}

                firebaseService.updateUserRoleAndPermissions(
                  uid = user.uid,
                  addressId = target.addressId,
                  userRole = currentUserRole,
                  osbbId = target.osmdId,
                  displayName = combinedName
                )
                chatViewModel.subscribeToResidentCounters(user.uid, target.osmdId, target.addressId)
              } else {
                _uiState.update { it.copy(apartments = emptyList(), mainLoading = false) }
              }
            }
            is Resource.Error -> {
              Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")
              _uiState.update { it.copy(mainLoading = false) }
            }
            is Resource.Loading -> {
              // В Loading ничего не делаем с mainLoading, чтобы не перебивать "Умный лоадер"
              Log.d("YkisLog", "$methodName: [LOADING]...")
            }
          }
        }
      } else {
        // 4. ЛОГИКА АДМИНА
        apartmentService.getOsbbApartmentsList(user.osbbId).collect { result ->
          if (firebaseService.currentUser == null) return@collect

          when (result) {
            is Resource.Success -> {
              val allApartments = result.data ?: emptyList()
              Log.d("YkisLog", "$methodName: [ADMIN] Успех. Квартир: ${allApartments.size}")

              withContext(Dispatchers.Default) {
                _uiState.update { it.copy(
                  apartments = allApartments,
                  mainLoading = false // ВЫКЛЮЧАЕМ лоадер
                )}
              }

              firebaseService.updateUserRoleAndPermissions(user.uid, 0, currentUserRole, user.osbbId, user.name ?: "Адмін")
              chatViewModel.trackUserIdentifiersWithRole(currentUserRole, user.osbbId)
            }
            is Resource.Error -> {
              Log.e("YkisLog", "$methodName: [ADMIN ERROR] ${result.message}")
              _uiState.update { it.copy(mainLoading = false) }
            }
            is Resource.Loading -> { Log.d("YkisLog", "$methodName: [ADMIN LOADING]...") }
          }
        }
      }
    }
  }






  fun resetToAdminMode() {
    val methodName = "ApartmentViewModel.resetToAdminMode()"
    Log.d("YkisLog", "$methodName: [RESET] Возврат к списку чатов")

    _uiState.update { it.copy(
      apartments = emptyList(), // Очищаем список, чтобы навигация переключилась на UserList
      addressId = 0,
      address = "",
      apartment = ApartmentEntity(),
      mainLoading = true // Запускаем лоадер для свежей синхронизации
    ) }

    // Перезагружаем профиль и список чатов
    observeUserProfile()
  }

  fun switchToResidentMode(targetApartment: ApartmentEntity) {
    val methodName = "ApartmentViewModel.switchToResidentMode()"
    Log.d("YkisLog", "$methodName: [SWITCH] Переход в режим жильца для квартиры: ${targetApartment.address}")

    viewModelScope.launch {
      val combinedName = "${targetApartment.address} | ${targetApartment.nanim ?: ""}"

      // 1. Обновляем UI стейт так, будто мы StandardUser
      _uiState.update { currentState ->
        currentState.copy(
          userRole = UserRole.StandardUser, // UI теперь думает, что мы жилец
          addressId = targetApartment.addressId,
          osbbId = targetApartment.osmdId,
          osmdId = targetApartment.osmdId,
          address = targetApartment.address,
          displayName = combinedName,
          apartment = targetApartment,
          mainLoading = false
        )
      }

      // 2. Запускаем мониторинг счетчиков именно этой квартиры
      // Используем UID админа, но addressId выбранной квартиры
      chatViewModel.subscribeToResidentCounters(
        uid = _uiState.value.uid ?: "",
        osbbId = targetApartment.osmdId,
        addressId = targetApartment.addressId
      )

      Log.d("YkisLog", "$methodName: [SUCCESS] Интерфейс переключен на квартиру ${targetApartment.addressId}")
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
  private fun handleAdminResult(
    result: Resource<GetSimpleResponse>,
    restartApp: () -> Unit
  ) {
    val methodName = "ApartmentViewModel.handleAdminResult()"

    when (result) {
      is Resource.Success -> {
        val data = result.data ?: return
        val currentUid = firebaseService.uid ?: ""
        val mappedRole = UserRole.fromString(data.userRole)
        val newOsbbId = data.osbbId

        Log.d("YkisLog", "$methodName: [SUCCESS] Код принят. Роль: ${mappedRole.name}, OSBB: $newOsbbId")

        viewModelScope.launch {
          try {
            // 1. СИНХРОНИЗАЦИЯ С FIREBASE (Админ не привязан к addressId, передаем 0)
            Log.d("YkisLog", "$methodName: [STEP 1] Запись прав админа в Firestore")
            firebaseService.updateUserRoleAndPermissions(
              uid = currentUid,
              addressId = 0,
              userRole = mappedRole, // Передаем Enum (или .name если метод требует String)
              osbbId = newOsbbId,
              displayName = null
            )

            // 2. ПОЛУЧЕНИЕ ПОЛНОГО СПИСКА КВАРТИР ОСББ
            Log.d("YkisLog", "$methodName: [STEP 2] Загрузка всех квартир дома для админа")
            apartmentService.getOsbbApartmentsList(newOsbbId).collect { apartmentResult ->
              if (apartmentResult is Resource.Success) {
                val apartments = apartmentResult.data ?: emptyList()
                val first = apartments.firstOrNull()

                val combinedName = if (first != null) "${first.address} | ${first.nanim ?: ""}" else (data.addressId ?: "")

                // 3. ОБНОВЛЕНИЕ ЛОКАЛЬНОГО UI STATE (Имитируем StandardUser для первой квартиры)
                Log.d("YkisLog", "$methodName: [STEP 3] Инициализация UI первой квартирой: ${first?.address}")
                _uiState.update { currentState ->
                  currentState.copy(
                    apartments = apartments,
                    addressId = first?.addressId ?: 0,
                    osmdId = newOsbbId,
                    osbbId = newOsbbId,
                    address = first?.address ?: "",
                    userRole = mappedRole,
                    mainLoading = false
                  )
                }

                // 4. ТРЕКИНГ ЧАТОВ (Админ слушает все чаты организации)
                chatViewModel.trackUserIdentifiersWithRole(mappedRole, newOsbbId)

                _secretCode.value = ""
                SnackbarManager.showMessage(R.string.success_admin_auth)

                Log.d("YkisLog", "$methodName: [FINISH] Перезапуск через 150мс")
                delay(150)
                restartApp()
              }

              if (apartmentResult is Resource.Error) {
                Log.e("YkisLog", "$methodName: [ERROR] Не удалось загрузить список домов")
                _uiState.update { it.copy(mainLoading = false) }
              }
            }

          } catch (e: Exception) {
            Log.e("YkisLog", "$methodName: [CRITICAL ERROR] ${e.message}")
            _uiState.update { it.copy(mainLoading = false) }
            SnackbarManager.showMessage(R.string.error_admin_auth)
          }
        }
      }

      is Resource.Error -> {
        Log.e("YkisLog", "$methodName: [API ERROR] ${result.resourceMessage}")
        _uiState.update { it.copy(mainLoading = false) }
        SnackbarManager.showMessage(result.resourceMessage ?: R.string.error_invalid_admin_code)
      }

      is Resource.Loading -> {
        Log.d("YkisLog", "$methodName: [LOADING] Проверка секретного кода...")
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
  // [СТАБИЛЬНАЯ ВЕРСИЯ]
  // [СТАБИЛЬНАЯ ВЕРСИЯ]
  // В начале ApartmentViewModel добавь:
  private var lastProcessingAddressId: Int = -1

  fun getApartment(addressId: Int = uiState.value.addressId) {
    val methodName = "ApartmentViewModel.getApartment($addressId)"

    // 1. АТОМАРНЫЙ БАРЬЕР (Предотвращает гонку состояний)
    if (addressId <= 0) return

    // Если мы УЖЕ в процессе загрузки этого ID или он УЖЕ загружен — выходим мгновенно
    if (addressId == lastProcessingAddressId ||
      (addressId == uiState.value.apartment.addressId && !uiState.value.apartmentLoading)) {
      Log.d("YkisLog", "$methodName -> ATOMIC SKIP")
      return
    }

    // Фиксируем намерение загрузить этот ID
    lastProcessingAddressId = addressId

    val currentUid = uid ?: ""

    apartmentService.getApartment(addressId = addressId, uid = currentUid).onEach { result ->
      when (result) {
        is Resource.Success -> {
          val data = result.data ?: ApartmentEntity()
          Log.d("YkisLog", "$methodName -> SUCCESS (Final)")

          // [СТАБИЛЬНАЯ ВЕРСИЯ - ИСПРАВЛЕНИЕ НАЗВАНИЯ]
          _uiState.update { currentState ->
            currentState.copy(
              apartment = data,
              addressId = data.addressId,
              address = data.address,
              osbbId = data.osmdId,
              osmdId = data.osmdId,
              // ИСПРАВЛЕНИЕ ТУТ:
              // Если data.osbb содержит нормальное название, оставляем его.
              // Если там пусто или "0", можно выводить "Мой ОСББ" как запасной вариант.
              osbb = if (data.osbb.isNullOrBlank() || data.osbb == "0") "Мой ОСББ" else data.osbb,
              apartmentLoading = false,
            )
          }


          _contactUiState.update { currentContact ->
            currentContact.copy(
              addressId = data.addressId,
              email = data.email,
              phone = data.phone,
              address = data.address
            )
          }

          // Сбрасываем флаг только после ПОЛНОЙ обработки данных
          // Можно добавить небольшую задержку, чтобы UI успел "успокоиться"
          delay(200)
          if (lastProcessingAddressId == addressId) {
            // Разблокируем только если за это время не начали грузить другой ID
          }
        }

        is Resource.Error -> {
          Log.e("YkisLog", "$methodName -> ERROR: ${result.message}")
          lastProcessingAddressId = -1 // Разблокируем при ошибке
          _uiState.update { it.copy(apartmentLoading = false) }
        }

        is Resource.Loading -> {
          Log.d("YkisLog", "$methodName -> LOADING")
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
