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
    val actualUid = firebaseService.uid ?: run {
      Log.e("YkisLog", "$methodName: [ABORT] UID is null")
      return
    }

    Log.d("YkisLog", "$methodName: [START] actualUid: $actualUid")

    // Больше не используем SKIP по флагу, так как нам нужно обновлять роль на лету.
    // Вместо этого просто перезапускаем Job, если он был.
    observeJob?.cancel()

    observeJob = viewModelScope.launch {
      // Гарантируем, что лоадер включен в начале процесса
      _uiState.update { it.copy(mainLoading = true) }

      try {
        // 1. ПОЛУЧЕНИЕ ПРОФИЛЯ (Всегда свежий из Firebase)
        val user = withContext(Dispatchers.IO) { firebaseService.getUserProfile() }
        val currentUserRole = UserRole.fromString(user.userRole)

        // Присваиваем системные ID для организаций (9999, 9998, 9997)
        val currentOsbbId = if (currentUserRole != UserRole.StandardUser &&
          currentUserRole != UserRole.OsbbUser && user.osbbId == 0) {
          when(currentUserRole) {
            UserRole.VodokanalUser -> 9999
            UserRole.YtkeUser -> 9998
            UserRole.TboUser -> 9997
            else -> 0
          }
        } else user.osbbId

        Log.d("YkisLog", "$methodName: [PROFILE_LOADED] Role: $currentUserRole, ID: $currentOsbbId")

        // Обновляем базовые поля стейта
        _uiState.update { it.copy(
          uid = user.uid,
          userRole = currentUserRole,
          osbbId = currentOsbbId,
          osmdId = currentOsbbId,
          displayName = user.name ?: ""
        )}

        // 2. ВЕТВЛЕНИЕ ЛОГИКИ ПО РОЛЯМ
        when (currentUserRole) {
          UserRole.StandardUser -> {
            // ЛОГИКА ЖИЛЬЦА
            apartmentService.getApartmentList(user.uid).collect { result ->
              handleStandardUserResult(result, user.uid, currentUserRole)
            }
          }
          UserRole.OsbbUser -> {
            // ЛОГИКА АДМИНА ОСББ
            apartmentService.getOsbbApartmentsList(currentOsbbId).collect { result ->
              handleOsbbAdminResult(result, user.uid, currentUserRole, currentOsbbId, user.name)
            }
          }
          else -> {
            // ЛОГИКА ОРГАНИЗАЦИЙ (РАЙОНЫ)
            apartmentService.getRaionList(user.uid).collect { result ->
              handleOrganizationResult(result, user.uid, currentUserRole, currentOsbbId, user.name)
            }
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL ERROR] ${e.message}")
        _uiState.update { it.copy(mainLoading = false) }
      }
    }
  }

  // Вспомогательный метод для админа ОСББ
  private suspend fun handleOsbbAdminResult(
    result: Resource<List<ApartmentEntity>>,
    uid: String,
    role: UserRole,
    osbbId: Int,
    name: String?
  ) {
    _uiState.update { state ->
      when (result) {
        is Resource.Success -> {
          Log.d("YkisLog", "handleOsbbAdminResult: [SUCCESS] Загружено ${result.data?.size} кв.")
          state.copy(
            apartments = result.data ?: emptyList(),
            listMode = ListMode.APARTMENTS,
            mainLoading = false // ВЫКЛЮЧАЕМ ЛОАДЕР
          )
        }
        is Resource.Error -> {
          Log.e("YkisLog", "handleOsbbAdminResult: [ERROR] ${result.message}")
          state.copy(mainLoading = false)
        }
        is Resource.Loading -> state.copy(mainLoading = true)
      }
    }

    if (result is Resource.Success) {
      // Синхронизируем права в Firestore и запускаем трекер чатов
      firebaseService.updateUserRoleAndPermissions(uid, 0, role, osbbId, name)
      chatViewModel.trackUserIdentifiersWithRole(role, osbbId)
    }
  }

// ... внутри ApartmentViewModel ...

  // 1. ЛОГИКА ЖИЛЬЦА
  private suspend fun handleStandardUserResult(
    result: Resource<List<ApartmentEntity>>,
    uid: String,
    role: UserRole
  ) {
    _uiState.update { state ->
      when (result) {
        is Resource.Success -> {
          val apartments = result.data ?: emptyList()
          if (apartments.isNotEmpty()) {
            val target = apartments.find { it.addressId == state.addressId } ?: apartments.first()
            val combinedName = "${target.address} | ${target.nanim ?: ""}"

            // Запускаем подписки на чаты и обновляем права
            chatViewModel.subscribeToAllMyApartments(uid, target.osmdId, apartments.map { it.addressId })
            firebaseService.updateUserRoleAndPermissions(uid, target.addressId, role, target.osmdId, combinedName)

            state.copy(
              apartments = apartments,
              isApartmentsLoaded = true,
              addressId = target.addressId,
              address = target.address,
              displayName = combinedName,
              mainLoading = false
            )
          } else state.copy(mainLoading = false)
        }
        is Resource.Error -> state.copy(mainLoading = false)
        is Resource.Loading -> state.copy(mainLoading = true)
      }
    }
  }

  // 2. ЛОГИКА ОРГАНИЗАЦИЙ (Водоканал, ТБО и т.д.)
  private suspend fun handleOrganizationResult(
    result: Resource<List<RaionEntity>>,
    uid: String,
    role: UserRole,
    osbbId: Int,
    name: String?
  ) {
    _uiState.update { state ->
      when (result) {
        is Resource.Success -> {
          Log.d("YkisLog", "handleOrg: [SUCCESS] Загружено ${result.data?.size} районов")
          state.copy(
            raions = result.data ?: emptyList(),
            listMode = ListMode.RAIONS, // Переключаем Drawer на выбор районов
            mainLoading = false
          )
        }
        is Resource.Error -> state.copy(mainLoading = false)
        is Resource.Loading -> state.copy(mainLoading = true)
      }
    }

    if (result is Resource.Success) {
      firebaseService.updateUserRoleAndPermissions(uid, 0, role, osbbId, name)
      chatViewModel.trackUserIdentifiersWithRole(role, osbbId)
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

    // 1. УМНЫЙ ЗАМОК: не даем дублировать запросы
    if (state.addressId == addressId && state.apartmentLoading) return
    if (state.addressId == addressId && state.apartment.addressId != 0) {
      Log.d("YkisLog", "$methodName($addressId) -> ATOMIC SKIP")
      return
    }

    Log.d("YkisLog", "$methodName: [FORCE_FETCH] Загрузка о/р $addressId")

    lastProcessingAddressId = addressId
    val currentUid = uid ?: ""

    apartmentService.getApartment(addressId = addressId, uid = currentUid).onEach { result ->
      when (result) {
        is Resource.Success -> {
          val data = result.data ?: ApartmentEntity()
          val currentUserRole = _uiState.value.userRole
          val isStandardUser = currentUserRole == UserRole.StandardUser

          _uiState.update { currentState ->
            currentState.copy(
              apartment = data,
              addressId = data.addressId,
              address = data.address,
              // Сохраняем системный ID (9999 и т.д.), если он уже задан
              osbbId = if (currentState.osbbId > 9000) currentState.osbbId else data.osmdId,
              osmdId = if (currentState.osmdId > 9000) currentState.osmdId else data.osmdId,
              apartmentLoading = false
            )
          }

          // 2. РАЗДЕЛЕНИЕ ЛОГИКИ (КРИТИЧЕСКИЙ ФИКС ИМЕНИ)
          if (isStandardUser) {
            // ЖИЛЕЦ: обновляем чаты и прописываем "Адрес | Фамилия" в профиль
            val combinedName = "${data.address} | ${data.nanim ?: ""}"
            Log.d("YkisLog", "$methodName: [RESIDENT_SYNC] Данные жильца обновлены")

            chatViewModel.subscribeToAllMyApartments(currentUid, data.osmdId, listOf(data.addressId))

            // Обновляем профиль данными квартиры
            firebaseService.updateUserRoleAndPermissions(
              uid = currentUid,
              addressId = data.addressId,
              userRole = currentUserRole,
              osbbId = data.osmdId,
              displayName = combinedName
            )
          } else {
            // АДМИН: просто смотрим. НИКАКИХ обновлений профиля (updateUserRoleAndPermissions) здесь!
            // Это гарантирует, что имя админа (Sergey Ykis) не затрется именем жильца.
            Log.d("YkisLog", "$methodName: [ADMIN_VIEW] Просмотр о/р ${data.addressId}. Профиль не затронут.")
          }
        }

        is Resource.Error -> {
          Log.e("YkisLog", "$methodName -> ERROR: ${result.message}")
          _uiState.update { it.copy(apartmentLoading = false) }
        }

        is Resource.Loading -> {
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


  fun setAddressId(addressId: Int) {
    val methodName = "ApartmentVM.setAddressId"
    val currentState = _uiState.value
    val isResident = currentState.userRole == UserRole.StandardUser

    Log.d("YkisLog", "$methodName: [START] Поиск ID: $addressId")

    // 1. УМНЫЙ ПОИСК (сначала в своих, потом в списке админа)
    val target = currentState.apartments.find { it.addressId == addressId }
      ?: _drawerApartments.value.find { it.addressId == addressId }

    if (target != null) {
      val finalOsbbId = if (currentState.osbbId > 9000) currentState.osbbId else target.osmdId
      // Формируем имя для жильца: "Адрес | Фамилия"
      val combinedName = "${target.address} | ${target.nanim ?: ""}"

      Log.d("YkisLog", "$methodName: [MATCH_FOUND] ${target.address}")

      _uiState.update { state ->
        state.copy(
          addressId = target.addressId,
          apartment = target,
          address = target.address,
          houseId = target.houseId,
          // Если ты админ - НЕ меняем displayName в стейте на имя жильца!
          displayName = if (isResident) combinedName else state.displayName,
          osbbId = finalOsbbId,
          osmdId = finalOsbbId,
          apartmentLoading = false
        )
      }

      // 2. СИНХРОНИЗАЦИЯ ПРАВ (КРИТИЧЕСКИЙ ФИКС)
      if (isResident) {
        // Только для ЖИЛЬЦА обновляем профиль данными квартиры
        viewModelScope.launch {
          firebaseService.updateUserRoleAndPermissions(
            uid = currentState.uid ?: "",
            addressId = target.addressId,
            userRole = currentState.userRole,
            osbbId = finalOsbbId,
            displayName = combinedName
          )
        }

        Log.d("YkisLog", "$methodName: [USER_SYNC] Обновление подписок на бейджи")
        chatViewModel.subscribeToAllMyApartments(
          uid = currentState.uid ?: "",
          osbbId = finalOsbbId,
          apartments = currentState.apartments.map { it.addressId }
        )
      } else {
        // Для АДМИНА — только локальное переключение. Профиль в Firestore НЕ ТРОГАЕМ.
        Log.d("YkisLog", "$methodName: [ADMIN_MODE] Профиль защищен (Name: ${currentState.displayName})")

        // Трекер админа запускается один раз при входе, здесь его дублировать не обязательно,
        // но если нужно - вызываем без сброса прав в облаке.
        chatViewModel.trackUserIdentifiersWithRole(currentState.userRole, finalOsbbId)
      }

      Log.d("YkisLog", "$methodName: [SUCCESS] State Updated. AddressId: $addressId")

    } else {
      Log.w("YkisLog", "$methodName: [NOT_FOUND] Устанавливаем только ID.")
      _uiState.update { it.copy(addressId = addressId) }
    }
  }
















}
