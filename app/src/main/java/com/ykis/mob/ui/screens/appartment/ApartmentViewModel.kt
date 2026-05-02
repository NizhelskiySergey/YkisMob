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

import android.R.attr.data
import android.os.Process.myUid
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.ext.isValidEmail
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.domain.UserRole
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.HouseEntity
import com.ykis.mob.domain.apartment.RaionEntity
import com.ykis.mob.firebase.service.repo.AuthStateResponse
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.screens.bti.ContactUIState
import com.ykis.mob.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

enum class ListMode { RAIONS, HOUSES, APARTMENTS }


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

  var lastLoadedAddressId: Int = -1
  private var observeJob: Job? = null // Ссылка на текущую работу
  private var isHandlingResult = false // Флаг-предохранитель
  private val _apartment = MutableStateFlow(ApartmentEntity())
  val apartment: StateFlow<ApartmentEntity> get() = _apartment.asStateFlow()
  private var isObservingStarted = false
  private val _secretCode = MutableStateFlow("")
  val secretCode: StateFlow<String> = _secretCode.asStateFlow()

  private var lastHandledResultId: Int? = null // Храним ID последней обработанной операции


  // LaunchScreen
  private val _showError = MutableStateFlow(false)
  val showError: StateFlow<Boolean> = _showError.asStateFlow()

  // Используйте val вместо fun
  val authState: AuthStateResponse = firebaseService.getAuthState(viewModelScope)


  private val _drawerHouses = MutableStateFlow<List<HouseEntity>>(emptyList())
  val drawerHouses = _drawerHouses.asStateFlow()

  private val _drawerApartments = MutableStateFlow<List<ApartmentEntity>>(emptyList())
  val drawerApartments = _drawerApartments.asStateFlow()
  // Во ViewModel (там же, где и _drawerHouses)


  private val _drawerLoading = MutableStateFlow(false)
  val drawerLoading = _drawerLoading.asStateFlow()

  private val _contactUiState = MutableStateFlow(ContactUIState())
  val contactUIState: StateFlow<ContactUIState> = _contactUiState.asStateFlow()
  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  // Список, который видит UI (отфильтрованный)
  val filteredApartments = combine(
    _searchQuery,
    _uiState,
    _drawerHouses,
    _drawerApartments
  ) { query, state, houses, drApts ->
    // 1. Если поиск пустой, возвращаем пустой список (UI сам покажет основной контент)
    if (query.isEmpty()) return@combine emptyList()

    // 2. Определяем, какой список фильтровать в зависимости от режима
    when (state.listMode) {
      ListMode.HOUSES -> {
        houses.filter { it.house.contains(query, ignoreCase = true) }
          .map { ApartmentEntity(address = it.house, addressId = it.houseId) }
      }
      ListMode.APARTMENTS -> {
        // Для админа берем из drawerApartments, для жильца из state.apartments
        val source = if (state.userRole != UserRole.StandardUser && state.userRole != UserRole.OsbbUser) {
          drApts
        } else {
          state.apartments
        }

        source.filter {
          it.address.contains(query, ignoreCase = true) ||
            (it.nanim?.contains(query, ignoreCase = true) ?: false) ||
            it.addressId.toString().contains(query)
        }
      }
      ListMode.RAIONS -> emptyList() // Районы обычно не фильтруем в Rail
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  fun onSearchQueryChanged(newQuery: String) {
    _searchQuery.value = newQuery
  }
  fun clearState() {
    val methodName = "ApartmentViewModel.clearState"
    Log.d("YkisLog", "$methodName: [FORCE_RESET] Полная очистка")

    observeJob?.cancel()
    lastLoadedAddressId = -1 // Сбрасываем предохранитель загрузки

    _uiState.update {
      // Создаем чистый стейт, где UID специально ставим в "empty",
      // чтобы LaunchedEffect в UI увидел разницу с новым Firebase UID
      BaseUIState(uid = "empty", mainLoading = false)
    }
  }
  // 1. Загрузка районов (вызываем, например, в init или при входе админа организации)


  // 2. Обработка выбора Района в Dropdown
  fun onRaionSelected(raion: RaionEntity) {
    val methodName = "ApartmentViewModel.onRaionSelected"
    val raionIdInt = raion.raionId ?: 0

    Log.d("YkisLog", "$methodName: [START] Район: ${raion.raion} (ID: $raionIdInt)")

    // 1. ПЕРЕКЛЮЧАЕМ РЕЖИМ СПИСКА
    // ВАЖНО: Мы меняем только listMode и ID района.
    // addressId НЕ ТРОГАЕМ, чтобы чат на фоне не закрылся!
    _uiState.update { it.copy(
      selectedRegionId = raionIdInt,
      listMode = ListMode.HOUSES
    )}

    _drawerLoading.value = true

    viewModelScope.launch {
      apartmentService.getHouseList(raionIdInt).collect { result ->
        when (result) {
          is Resource.Success -> {
            val houses = result.data ?: emptyList()
            Log.d("YkisLog", "$methodName: [SUCCESS] Домов в Drawer: ${houses.size}")

            // 2. ОБНОВЛЯЕМ СПЕЦИАЛЬНЫЙ СПИСОК ДЛЯ ДРАЙВЕРА
            _drawerHouses.value = houses
            _drawerLoading.value = false
          }
          is Resource.Error -> {
            Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")
            _drawerLoading.value = false
            SnackbarManager.showMessage(result.message ?: "Ошибка загрузки домов")
          }
          is Resource.Loading -> {
            _drawerLoading.value = true
          }
        }
      }
    }
  }


  fun goBackLevel() {
    val methodName = "ApartmentViewModel.goBackLevel"

    _uiState.update { state ->
      val newMode = when (state.listMode) {
        // Если мы в Квартирах -> возвращаемся к Домам
        ListMode.APARTMENTS -> {
          Log.d("YkisLog", "$methodName: Возврат к списку домов")
          ListMode.HOUSES
        }
        // Если мы в Домах -> возвращаемся к Районам
        ListMode.HOUSES -> {
          Log.d("YkisLog", "$methodName: Возврат к списку районов")
          // При возврате к районам можно очистить список домов
          _drawerHouses.value = emptyList()
          ListMode.RAIONS
        }
        // Если мы уже в Районах -> ничего не делаем
        ListMode.RAIONS -> ListMode.RAIONS
      }
      state.copy(listMode = newMode)
    }
  }


  fun onHouseSelected(houseId: Int) {
    val methodName = "ApartmentViewModel.onHouseSelected"
    Log.d("YkisLog", "$methodName: [START] Загрузка квартир для дома ID: $houseId")

    _drawerLoading.value = true

    // КРИТИЧЕСКИЙ ФИКС: Переключаем режим, чтобы UI понял, что пора рисовать квартиры
    _uiState.update { it.copy(
      selectedHouseId = houseId,
      listMode = ListMode.APARTMENTS // Теперь LazyColumn переключится на drawerApartments
    )}

    viewModelScope.launch {
      apartmentService.getOsbbApartmentsList(houseId, isHouseSearch = true).collect { result ->
        when (result) {
          is Resource.Success -> {
            val apartments = result.data ?: emptyList()
            Log.d("YkisLog", "$methodName: [SUCCESS] Получено: ${apartments.size} кв.")

            _drawerApartments.value = apartments
            _drawerLoading.value = false
          }
          is Resource.Error -> {
            Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")
            _drawerLoading.value = false
            // Если ошибка — лучше вернуть режим назад к домам
            _uiState.update { it.copy(listMode = ListMode.HOUSES) }
            SnackbarManager.showMessage(result.message ?: "Помилка завантаження")
          }
          is Resource.Loading -> {
            _drawerLoading.value = true
          }
        }
      }
    }
  }



  fun onSecretCodeChanged(newCode: String) {
    _secretCode.value = newCode
  }
  fun onAppStart(): String {
    val userExists = firebaseService.hasUser
    val emailVerified = firebaseService.isEmailVerified

    Log.d("YkisLog", "AppStart: UserExists=$userExists, Verified=$emailVerified")

    return if (userExists && emailVerified == true) {
      // Если юзер есть, мы ПРОВЕРЯЕМ, не остались ли в памяти старые квартиры
      if (_uiState.value.uid != null && _uiState.value.uid != firebaseService.uid) {
        Log.w("YkisLog", "AppStart: Обнаружен конфликт UID в памяти! Чистим стейт.")
        clearState() // Тот самый метод очистки, который мы писали
      }
      Graph.APARTMENT
    } else {
      // Если юзера нет — принудительно чистим всё перед уходом на логин
      clearState()
      Graph.AUTHENTICATION
    }
  }

  /**
   * Инициализирует профиль пользователя при старте приложения.
   * Загружает роль, ID организации и список квартир (для жильцов).
   */
  fun observeUserProfile() {
    val methodName = "ApartmentViewModel.observeUserProfile"
    val actualUid = firebaseService.uid ?: return

    Log.d("YkisLog", "$methodName: [START] actualUid: $actualUid")


    // Если загрузка уже идет для этого UID — выходим
    if (isObservingStarted) {
      Log.d("YkisLog", "ApartmentViewModel: [SKIP] Загрузка уже активна для $actualUid")
      return
    }

    isObservingStarted = true
    Log.d("YkisLog", "ApartmentViewModel.observeUserProfile: isObservingStarted: $isObservingStarted")

    if (_uiState.value.uid != null && _uiState.value.uid != actualUid) {
      Log.w("YkisLog", "$methodName: [SESSION_MISMATCH] Очистка стейта.")
      clearState()
    }

    observeJob?.cancel()
    observeJob = viewModelScope.launch {
      val currentUser = firebaseService.currentUser
      if (currentUser == null) {
        _uiState.update { it.copy(mainLoading = false) }
        return@launch
      }

      // 1. ПОЛУЧЕНИЕ ПРОФИЛЯ
      val user = withContext(Dispatchers.IO) { firebaseService.getUserProfile() }

      val currentUserRole = UserRole.fromString(user.userRole)
      // Если это организация и ID в базе 0, подставляем системный ID для логики чатов
      val currentOsbbId = if (currentUserRole != UserRole.StandardUser &&
        currentUserRole != UserRole.OsbbUser &&
        user.osbbId == 0) {
        when(currentUserRole) {
          UserRole.VodokanalUser -> 9999
          UserRole.YtkeUser -> 9998
          UserRole.TboUser -> 9997
          else -> 0
        }
      } else user.osbbId

      Log.d("YkisLog", "$methodName: [PROFILE_LOADED] Role: $currentUserRole, osbbId: $currentOsbbId")

      // 2. ОБНОВЛЯЕМ СТЕЙТ СРАЗУ (Чтобы убрать лоадер в MainApartmentScreen)
      _uiState.update { it.copy(
        uid = user.uid,
        userRole = currentUserRole,
        osbbId = currentOsbbId,
        osmdId = currentOsbbId,
        displayName = user.name ?: "",
        mainLoading = false // Снимаем глобальный лоадер, так как профиль есть
      )}

      // 3. ЛОГИКА ЖИЛЬЦА
      if (currentUserRole == UserRole.StandardUser) {
        apartmentService.getApartmentList(user.uid).collect { result ->
          if (firebaseService.currentUser == null) return@collect
          when (result) {
            is Resource.Success -> {
              val apartments = result.data ?: emptyList()
              if (apartments.isNotEmpty()) {
                val target = apartments.find { it.addressId == _uiState.value.addressId } ?: apartments.first()
                val combinedName = "${target.address} | ${target.nanim ?: ""}"

                _uiState.update { it.copy(
                  apartments = apartments,
                  isApartmentsLoaded = true,
                  addressId = target.addressId,
                  address = target.address,
                  displayName = combinedName,
                  mainLoading = false
                )}
                chatViewModel.subscribeToAllMyApartments(user.uid, target.osmdId, apartments.map { it.addressId })
                firebaseService.updateUserRoleAndPermissions(user.uid, target.addressId, currentUserRole, target.osmdId, combinedName)
              } else {
                _uiState.update { it.copy(mainLoading = false) }
              }
            }
            is Resource.Error -> _uiState.update { it.copy(mainLoading = false) }
            is Resource.Loading -> _uiState.update { it.copy(mainLoading = true) }
          }
        }
      } else {
        // 4. ЛОГИКА АДМИНА
        if (currentUserRole == UserRole.OsbbUser) {
          apartmentService.getOsbbApartmentsList(currentOsbbId).collect { result ->
            if (firebaseService.currentUser == null) return@collect
            _uiState.update { state ->
              when (result) {
                is Resource.Success -> state.copy(
                  apartments = result.data ?: emptyList(),
                  listMode = ListMode.APARTMENTS,
                  mainLoading = false
                )
                is Resource.Error -> state.copy(mainLoading = false)
                is Resource.Loading -> state.copy(mainLoading = true)
              }
            }
            if (result is Resource.Success) {
              firebaseService.updateUserRoleAndPermissions(user.uid, 0, currentUserRole, currentOsbbId, user.name)
              chatViewModel.trackUserIdentifiersWithRole(currentUserRole, currentOsbbId)
            }
          }
        } else {
          // ОРГАНИЗАЦИИ
          apartmentService.getRaionList(user.uid).collect { result ->
            if (firebaseService.currentUser == null) return@collect
            _uiState.update { state ->
              when (result) {
                is Resource.Success -> state.copy(
                  raions = result.data ?: emptyList(),
                  listMode = ListMode.RAIONS,
                  mainLoading = false
                )
                is Resource.Error -> state.copy(mainLoading = false)
                is Resource.Loading -> state.copy(mainLoading = true)
              }
            }
            if (result is Resource.Success) {
              // Обновляем в Firestore системный ID (9999 и т.д.), если там был 0
              firebaseService.updateUserRoleAndPermissions(user.uid, 0, currentUserRole, currentOsbbId, user.name)
              chatViewModel.trackUserIdentifiersWithRole(currentUserRole, currentOsbbId)
            }
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
    val methodName = "ApartmentVM.handleResult"

    if (isHandlingResult && result !is Resource.Loading) {
      Log.w("YkisLog", "$methodName: [SKIP] Повторный вызов проигнорирован")
      return
    }

    when (result) {
      is Resource.Loading -> {
        _uiState.update { it.copy(mainLoading = true) }
      }

      is Resource.Success -> {
        isHandlingResult = true

        val data = result.data ?: run { isHandlingResult = false; return }
        val newAddressId = data.addressId ?: 0
        val newOsbbId = data.osbbId ?: 0
        val newAddress = data.address ?: ""

        try {
          // 1. СИНХРОНИЗАЦИЯ (Firestore)
          Log.d("YkisLog", "$methodName: [STEP 1] Firestore Sync")
          firebaseService.updateUserRoleAndPermissions(
            uid = uid, addressId = newAddressId,
            userRole = UserRole.StandardUser, osbbId = newOsbbId, displayName = newAddress
          )

          // 2. ОБНОВЛЕНИЕ СПИСКА (Room)
          Log.d("YkisLog", "$methodName: [STEP 2] GetApartmentList")
          getApartmentList {
            // Используем отдельную корутину для завершения, чтобы не блокировать callback
            viewModelScope.launch(Dispatchers.Main) {

              // 3. ОБНОВЛЕНИЕ UI STATE
              _uiState.update { it.copy(
                addressId = newAddressId, osmdId = newOsbbId, osbbId = newOsbbId,
                address = newAddress, userRole = UserRole.StandardUser,
                mainLoading = false
              )}

              // 4. ПОДПИСКА НА ЧАТЫ (Тяжелая операция для UI)
              Log.d("YkisLog", "$methodName: [STEP 3] Чат-счетчики")
//              chatViewModel.subscribeToResidentCounters(uid, newOsbbId, newAddressId,newAddress)
              if (lastHandledResultId == newAddressId) return@launch
              lastHandledResultId = newAddressId

              chatViewModel.initResidentChats(
                uid = uid,
                osbbId = newOsbbId,
                addressId = newAddressId,
                addressText = newAddress,
                nanim = ""
              )

              _secretCode.value = ""
              SnackbarManager.showMessage(R.string.success_add_flat)

              // 5. ФИНАЛЬНАЯ ПАУЗА (Даем Compose 0.5 сек закрыть все группы)
              Log.d("YkisLog", "$methodName: [WAIT] Стабилизация Compose перед выходом...")
              delay(500)

              isHandlingResult = false
              Log.d("YkisLog", "$methodName: [FINISH] Навигация (restartApp)")
              restartApp()
            }
          }

        } catch (e: Exception) {
          Log.e("YkisLog", "$methodName: [CRITICAL ERROR] ${e.message}")
          isHandlingResult = false
          _uiState.update { it.copy(mainLoading = false) }
          SnackbarManager.showMessage(R.string.error_add_apartment)
        }
      }

      is Resource.Error -> {
        Log.e("YkisLog", "$methodName: [API ERROR] ${result.resourceMessage}")
        isHandlingResult = false
        _uiState.update { it.copy(mainLoading = false) }
        SnackbarManager.showMessage(result.resourceMessage ?: R.string.error_add_apartment)
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
    val methodName = "ApartmentViewModel.handleAdminResult"

    when (result) {
      is Resource.Success -> {
        val data = result.data ?: return
        val mappedRole = UserRole.fromString(data.userRole)
        val currentUid = firebaseService.uid ?: ""

        val newOsbbId = when(mappedRole) {
          UserRole.VodokanalUser -> 9999
          UserRole.YtkeUser -> 9998
          UserRole.TboUser -> 9997
          else -> data.osbbId
        }

        viewModelScope.launch {
          try {
            // Используем NonCancellable, чтобы запись в БД не прервалась при закрытии экрана
            withContext(NonCancellable) {
              Log.d("YkisLog", "$methodName: [STEP 1] Запись прав...")
              firebaseService.updateUserRoleAndPermissions(
                uid = currentUid,
                addressId = 0,
                userRole = mappedRole,
                osbbId = newOsbbId,
                displayName = null
              )
            }

            _uiState.update { it.copy(
              userRole = mappedRole,
              osbbId = newOsbbId,
              listMode = if (mappedRole == UserRole.OsbbUser) ListMode.APARTMENTS else ListMode.RAIONS,
              mainLoading = true
            )}

            observeUserProfile()
            chatViewModel.trackUserIdentifiersWithRole(mappedRole, newOsbbId)

            _secretCode.value = ""
            // Показываем успех ПЕРЕД перезапуском
            SnackbarManager.showMessage(R.string.success_admin_auth)

            Log.d("YkisLog", "$methodName: [FINISH] Перезапуск")
            delay(300) // Чуть больше задержка для записи
            restartApp()

          } catch (e: Exception) {
            Log.e("YkisLog", "$methodName: [CRITICAL] ${e.message}")
            _uiState.update { it.copy(mainLoading = false) }
          }
        }
      }

      is Resource.Loading -> _uiState.update { it.copy(mainLoading = true) }

      is Resource.Error -> {
        _uiState.update { it.copy(mainLoading = false) }
        SnackbarManager.showMessage(R.string.error_invalid_admin_code)
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
  // В начале ApartmentViewModel добавь:
  private var lastProcessingAddressId: Int = -1

  fun getApartment(addressId: Int = uiState.value.addressId) {
    val methodName = "ApartmentVM.getApartment"

    if (addressId <= 0) return

    val state = _uiState.value

    // 1. УМНЫЙ ЗАМОК: Пропускаем, только если ID совпадает И объект квартиры уже загружен и совпадает
//    if (state.addressId == addressId && state.apartment.addressId == addressId) {
//      Log.d("YkisLog", "$methodName($addressId) -> ATOMIC SKIP (Data already present)")
//      return
//    }

    Log.d("YkisLog", "$methodName: [FORCE_FETCH] Начинаем загрузку о/р $addressId. Старый ID в объекте: ${state.apartment.addressId}")

    lastProcessingAddressId = addressId
    val currentUid = uid ?: ""

    apartmentService.getApartment(addressId = addressId, uid = currentUid).onEach { result ->
      when (result) {
        is Resource.Success -> {
          val data = result.data ?: ApartmentEntity()
          Log.d("YkisLog", "$methodName -> SUCCESS для о/р ${data.addressId}")

          _uiState.update { currentState ->
            currentState.copy(
              apartment = data,
              addressId = data.addressId,
              address = data.address,
              // Сохраняем системный ID для организации, если он 9999
              osbbId = if (currentState.osbbId > 9000) currentState.osbbId else data.osmdId,
              houseId = data.houseId,
              osmdId = if (currentState.osmdId > 9000) currentState.osmdId else data.osmdId,
              osbb = if (data.osbb.isNullOrBlank() || data.osbb == "0") "Мій ОСББ" else data.osbb,
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

          // КРИТИЧНО ДЛЯ ЧАТА: Уведомляем трекер о смене квартиры
          delay(200)
          if (lastProcessingAddressId == addressId) {
            Log.d("YkisLog", "$methodName: [CHAT_SYNC] Синхронизация веток чата...")
            chatViewModel.trackUserIdentifiersWithRole(_uiState.value.userRole, _uiState.value.osbbId)
          }
        }

        is Resource.Error -> {
          Log.e("YkisLog", "$methodName -> ERROR: ${result.message}")
          lastProcessingAddressId = -1
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
                apartmentLoading = true,
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
    val methodName = "ApartmentVM.delete"
    val currentAddressId = _uiState.value.addressId
    val currentUid = firebaseService.uid ?: ""
    val currentOsbbId = uiState.value.osbbId

    if (currentAddressId == 0) return

    apartmentService.deleteApartment(addressId = currentAddressId, uid = currentUid)
      .onEach { result ->
        when (result) {
          is Resource.Loading -> {
            Log.d("YkisLog", "$methodName: [LOADING]")
            _uiState.update { it.copy(mainLoading = true) }
          }
          is Resource.Success -> {

            Log.d("YkisLog", "$methodName: [SUCCESS] Квартира удалена")
            // 2. ЧИСТИМ ЧАТЫ В FIREBASE
            chatViewModel.deleteChatThreads(uid, currentOsbbId, currentAddressId)

            SnackbarManager.showMessage(R.string.success_delete_flat)

            // Сразу после успеха сбрасываем текущий ID, чтобы UI не "завис" на старой квартире
            _uiState.update { it.copy(addressId = 0) }

            // Обновляем список квартир
            getApartmentList {
              val updatedList = _uiState.value.apartments
              Log.d("YkisLog", "$methodName: [UPDATE] Осталось квартир: ${updatedList.size}")

              if (updatedList.isEmpty()) {
                Log.d("YkisLog", "$methodName: [NAVIGATE] На экран добавления")
                _uiState.update { it.copy(
                  address = "",
                  apartment = ApartmentEntity(),
                  mainLoading = false
                )}
                onNavigate(AddApartmentScreen.route)
              } else {
                // Выбираем следующую доступную
                val nextApartment = updatedList.first()
                Log.d("YkisLog", "$methodName: [SWITCH] Переход на ID: ${nextApartment.addressId}")
                setAddressId(nextApartment.addressId)
                _uiState.update { it.copy(mainLoading = false) }
              }
            }
          }
          is Resource.Error -> {
            Log.e("YkisLog", "$methodName: [ERROR] ${result.message}")
            _uiState.update { it.copy(mainLoading = false) }
            SnackbarManager.showMessage(result.resourceMessage ?: R.string.error_delete_flat)
          }
        }
      }.launchIn(viewModelScope)
  }

//  fun setAddressId(id: Int) {
//    val selected = _uiState.value.apartments.find { it.addressId == id } ?: return
//    val currentUid = firebaseService.uid ?: ""
//
//    // Формируем заголовок "Адрес | Фамилия"
//    val combinedName = "${selected.address} | ${selected.nanim ?: ""}"
//
//    // Предохранитель от лишних обновлений
//    if (_uiState.value.addressId == id && _uiState.value.displayName == combinedName) return
//
//    _uiState.update { it.copy(
//      addressId = id,
//      address = selected.address,
//      displayName = combinedName,
//      osbbId = selected.osmdId,
//      osmdId = selected.osmdId,
//      houseId = selected.houseId
//    )}
//
//    if (currentUid.isNotEmpty() && _uiState.value.userRole == UserRole.StandardUser) {
//      viewModelScope.launch(Dispatchers.IO) {
//        firebaseService.updateUserRoleAndPermissions(
//          uid = currentUid,
//          userRole = UserRole.StandardUser,
//          osbbId = selected.osmdId,
//          addressId = id,           // Передаем AddressID
//          displayName = combinedName // Передаем сформированную строку
//        )
//      }
//    }
//    getApartment(id)
//  }

  fun setAddressId(addressId: Int) {
    val methodName = "ApartmentVM.setAddressId"
    val currentState = _uiState.value

    Log.d("YkisLog", "$methodName: [START] Поиск ID: $addressId")

    // 1. УМНЫЙ ПОИСК
    val target = currentState.apartments.find { it.addressId == addressId }
      ?: _drawerApartments.value.find { it.addressId == addressId }

    if (target != null) {
      val oldOsbbId = currentState.osbbId
      val finalOsbbId = if (oldOsbbId > 9000) oldOsbbId else target.osmdId

      Log.d("YkisLog", "$methodName: [MATCH_FOUND] ${target.address} | OSBB: $finalOsbbId")

      _uiState.update { state ->
        state.copy(
          addressId = target.addressId,
          apartment = target,
          address = target.address,
          houseId = target.houseId,
          displayName = "${target.address} | ${target.nanim ?: ""}",
          osbbId = finalOsbbId,
          osmdId = finalOsbbId,
          apartmentLoading = false
        )
      }

      // 2. СИНХРОНИЗАЦИЯ ПРАВ
      viewModelScope.launch {
        firebaseService.updateUserRoleAndPermissions(
          uid = currentState.uid ?: "",
          addressId = target.addressId,
          userRole = currentState.userRole,
          osbbId = finalOsbbId,
          displayName = currentState.displayName
        )
      }

      // 3. УМНАЯ СИНХРОНИЗАЦИЯ ЧАТА
      if (currentState.userRole != UserRole.StandardUser) {
        // Если АДМИН — запускаем поиск новых веток (трекер)
        Log.d("YkisLog", "$methodName: [ADMIN_SYNC] Запуск трекера для ${currentState.userRole}")
        chatViewModel.trackUserIdentifiersWithRole(currentState.userRole, finalOsbbId)
      } else {
        // Если ЖИТЕЛЬ — просто обновляем прослушивание своих бейджей
        Log.d("YkisLog", "$methodName: [USER_SYNC] Обновление подписок на бейджи")
        chatViewModel.subscribeToAllMyApartments(
          uid = currentState.uid ?: "",
          osbbId = finalOsbbId,
          apartments = currentState.apartments.map{it.addressId} // Список всех о/р жильца
        )
      }

      Log.d("YkisLog", "$methodName: [SUCCESS] State Updated. AddressId: ${_uiState.value.addressId}")

    } else {
      Log.w("YkisLog", "$methodName: [NOT_FOUND] Устанавливаем только ID.")
      _uiState.update { it.copy(addressId = addressId) }
    }
  }















}
