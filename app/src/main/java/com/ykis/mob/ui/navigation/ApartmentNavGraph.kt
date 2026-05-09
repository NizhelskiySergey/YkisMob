package com.ykis.mob.ui.navigation

import ModalNavigationDrawerContent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.DisplayFeature
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.YkisPamAppState
import com.ykis.mob.ui.navigation.components.ApartmentNavigationRail
import com.ykis.mob.ui.navigation.components.BottomNavigationBar
import com.ykis.mob.ui.rememberAppState
import com.ykis.mob.ui.screens.appartment.AddApartmentScreenContent
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.appartment.InfoApartmentScreen
import com.ykis.mob.ui.screens.chat.ChatScreenStateful
import com.ykis.mob.ui.screens.chat.ChatViewModel
import com.ykis.mob.ui.screens.chat.ServiceSelectorScreen
import com.ykis.mob.ui.screens.chat.UserListScreen
import com.ykis.mob.ui.screens.meter.MainMeterScreen
import com.ykis.mob.ui.screens.meter.MeterViewModel
import com.ykis.mob.ui.screens.service.MainServiceScreen
import com.ykis.mob.ui.screens.service.ServiceViewModel
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import com.ykis.mob.ui.screens.settings.NewSettingsViewModel
import com.ykis.mob.ui.screens.settings.SettingsScreenStateful
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainApartmentScreen(
  contentType: ContentType,
  navigationType: NavigationType,
  displayFeatures: List<DisplayFeature>,
  navController: NavHostController = rememberNavController(),
  apartmentViewModel: ApartmentViewModel,
  meterViewModel: MeterViewModel = koinViewModel(),
  serviceViewModel: ServiceViewModel = koinViewModel(),
  chatViewModel: ChatViewModel = koinViewModel(),
  newSettingsViewModel: NewSettingsViewModel = koinViewModel(),
  firebaseService: FirebaseService,
  rootNavController: NavHostController,
  appState: YkisPamAppState,
  onLaunch: () -> Unit,
  onDispose: () -> Unit,
  isRailExpanded: Boolean,
  onMenuClick: () -> Unit,
  navigateToWebView: (String) -> Unit,
) {
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val selectedDestination = navBackStackEntry?.destination?.route ?: InfoApartmentScreenDest.route
  val baseUIState by apartmentViewModel.uiState.collectAsStateWithLifecycle()
  val railWidth by animateDpAsState(
    targetValue = if (isRailExpanded) 280.dp else 80.dp,
    animationSpec = tween(400),
    label = "RailWidth"
  )
  val currentFirebaseUid = firebaseService.uid
  // 1. КОНТРОЛЬ ГОТОВНОСТИ ДАННЫХ
  val isDataReady = remember(baseUIState.uid, currentFirebaseUid, baseUIState.mainLoading,baseUIState.isApartmentsLoaded) {
    baseUIState.uid == currentFirebaseUid && !baseUIState.mainLoading
  }
  // 2. ВЫЧИСЛЕНИЕ СТАРТОВОГО МАРШРУТА
  val firstDestination = remember(baseUIState.userRole, baseUIState.addressId, isDataReady) {
    if (!isDataReady) return@remember "loading_buffer"
    val hasApartments = baseUIState.apartments.isNotEmpty()
    val isAddressSelected = baseUIState.addressId != 0 // ПРОВЕРКА: выбран ли адрес?
    when (baseUIState.userRole) {
      // ДЛЯ СЛУЖБ: Если адрес выбран (из поиска/списка) — на Info, иначе — на список чатов
      UserRole.VodokanalUser, UserRole.YtkeUser, UserRole.TboUser -> {
        if (isAddressSelected) InfoApartmentScreenDest.route else UserListScreen.route
      }
      // ДЛЯ ОСББ: Аналогично — если есть данные, идем в карточку
      UserRole.OsbbUser -> {
        if (isAddressSelected || hasApartments) InfoApartmentScreenDest.route else UserListScreen.route
      }
      // ДЛЯ ЖИТЕЛЯ: Если нет квартир — на добавление, если есть — на Info
      UserRole.StandardUser -> {
        if (!hasApartments) AddApartmentScreen.route else InfoApartmentScreenDest.route
      }
      else -> InfoApartmentScreenDest.route
    }
  }
  // 3. ФУНКЦИЯ ФИНАЛЬНОГО ВЫБОРА КВАРТИРЫ (Золотой фонд)
  val finalizeApartmentSelection: (Int) -> Unit = { id ->
    Log.d("YkisLog", "Main: [FINAL_SELECT] ID: $id")
    serviceViewModel.resetState()
    apartmentViewModel.setAddressId(id)
    // 1. Сначала полностью обнуляем стек
    cleanNavigateTo(navController, Graph.APARTMENT)
    coroutineScope.launch {
      if (drawerState.isOpen) drawerState.close()
      // 2. Даем навигатору "вздохнуть" после чистки стека
      delay(200)
      // 3. Переходим на Инфо БЕЗ попыток что-либо восстановить
      navController.navigate(InfoApartmentScreenDest.route) {
        popUpTo(navController.graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
        restoreState = false // Гарантируем чистоту
      }
    }
  }
  // Инициализация профиля
  LaunchedEffect(currentFirebaseUid) {
    if (baseUIState.uid == null) {
      apartmentViewModel.observeUserProfile()
    }
  }
  // 4. ГРАФ НАВИГАЦИИ (MovableContent для плавности переходов)
  val movableApartmentNavGraph =
    remember(baseUIState, contentType, navigationType, firstDestination, isDataReady) {
      movableContentOf {
        if (!isDataReady || firstDestination == "loading_buffer") {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        } else {
          ApartmentNavGraph(
            modifier = Modifier.fillMaxSize(),
            contentType = contentType,
            navigationType = navigationType,
            displayFeatures = displayFeatures,
            baseUIState = baseUIState,
            navController = navController,
            onDrawerClicked = { coroutineScope.launch { drawerState.open() } },
            apartmentViewModel = apartmentViewModel,
            rootNavController = rootNavController,
            firstDestination = firstDestination,
            meterViewModel = meterViewModel,
            serviceViewModel = serviceViewModel,
            chatViewModel = chatViewModel,
            newSettingsViewModel = newSettingsViewModel,
            closeContentDetail = {
              meterViewModel.closeContentDetail()
              serviceViewModel.closeContentDetail()
            },
            navigateToWebView = navigateToWebView,
          )
        }
      }
    }
  // 5. UI СТРУКТУРА (Телефон vs Планшет)
  if (navigationType == NavigationType.BOTTOM_NAVIGATION) {
    ModalNavigationDrawer(
      drawerState = drawerState,
      drawerContent = {
        ModalNavigationDrawerContent(
          baseUIState = baseUIState,
          selectedDestination = selectedDestination,
          chatViewModel = chatViewModel,
          apartmentViewModel = apartmentViewModel,
          navigateToDestination = { dest ->
            coroutineScope.launch {
              drawerState.close()
              navController.navigateWithPopUp(dest, firstDestination)
            }
          },
          onMenuClick = { coroutineScope.launch { drawerState.close() } },
          navigateToApartment = finalizeApartmentSelection,
          isApartmentsEmpty = baseUIState.addressId == 0
        )
      }
    ) {
      Scaffold(
        bottomBar = {
          // Показываем нижнее меню, если выбрана квартира ИЛИ если это админ в списке чатов
          val showBottomBar =
            baseUIState.addressId != 0 || baseUIState.userRole != UserRole.StandardUser
          if (showBottomBar) {
            BottomNavigationBar(
              selectedDestination = selectedDestination,
              onClick = { dest ->
                navController.navigateWithPopUp(dest, firstDestination)
              },
              chatViewModel = chatViewModel,
              baseUIState = baseUIState,
            )
          }
        }
      ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
          movableApartmentNavGraph()
        }
      }
    }
  } else {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Row(modifier = Modifier.fillMaxSize()) {
        ApartmentNavigationRail(
          modifier = Modifier.width(railWidth),
          baseUIState = baseUIState,
          selectedDestination = selectedDestination,
          chatViewModel = chatViewModel,
          apartmentViewModel = apartmentViewModel,
          isRailExpanded = isRailExpanded,
          onMenuClick = onMenuClick,
          navigateToDestination = { dest ->
            Log.d("YkisLog", "Rail: [NAV_SAFE] Переход на $dest")
            navController.navigate(dest) {
              // Очищаем стек до корня, чтобы не плодить копии
              popUpTo(navController.graph.findStartDestination().id) {
                saveState = false // ЗАПРЕЩАЕМ сохранять битое состояние
              }
              launchSingleTop = true
              restoreState = false // КРИТИЧЕСКИЙ ФИКС: отключаем восстановление
            }
          },
          navigateToApartment = finalizeApartmentSelection,
          isApartmentsEmpty = baseUIState.addressId == 0,
          railWidth = railWidth
        )
        VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Box(modifier = Modifier
          .weight(1f)
          .fillMaxHeight()) {
          movableApartmentNavGraph()
        }
      }
    }
  }
}

@Composable
fun ApartmentNavGraph(
  modifier: Modifier = Modifier,
  contentType: ContentType,
  navigationType: NavigationType,
  displayFeatures: List<DisplayFeature>,
  baseUIState: BaseUIState,
  onDrawerClicked: () -> Unit,
  navController: NavHostController,
  apartmentViewModel: ApartmentViewModel,
  rootNavController: NavHostController,
  firstDestination: String,
  meterViewModel: MeterViewModel,
  serviceViewModel: ServiceViewModel,
  chatViewModel: ChatViewModel,
  newSettingsViewModel: NewSettingsViewModel,
  closeContentDetail: () -> Unit,
  navigateToWebView: (String) -> Unit,
) {
  val appState = rememberAppState()
  val userList by chatViewModel.userList.collectAsStateWithLifecycle()
  Box(modifier = modifier.fillMaxSize()) {
    AnimatedVisibility(
      modifier = Modifier.align(Alignment.Center),
      visible = baseUIState.mainLoading,
      exit = fadeOut(tween(delayMillis = 300)),
      enter = fadeIn(tween(delayMillis = 300))
    ) {
      CircularProgressIndicator()
    }
    AnimatedVisibility(
      visible = !baseUIState.mainLoading,
      exit = fadeOut(tween(delayMillis = 300)),
      enter = fadeIn(tween(delayMillis = 300))
    ) {
      NavHost(
        modifier = Modifier.fillMaxSize(),
        navController = navController,
        route = Graph.APARTMENT,
        startDestination = firstDestination
      ) {
        composable(UserListScreen.route) {
          // 1. МОНИТОРИНГ: Только для админов (подгрузка списка жильцов/заявок)
          LaunchedEffect(baseUIState.userRole, baseUIState.osbbId) {
            val role = baseUIState.userRole
            if (role != UserRole.StandardUser) {
              val effectiveOsbbId = when (role) {
                UserRole.VodokanalUser -> 9999
                UserRole.YtkeUser -> 9998
                UserRole.TboUser -> 9997
                else -> baseUIState.osbbId ?: 0
              }
              Log.d("YkisLog", "NavGraph: [ADMIN_TRACK] Role: $role, OrgID: $effectiveOsbbId")
              chatViewModel.trackUserIdentifiersWithRole(role, effectiveOsbbId)
            }
          }

          UserListScreen(
            userList = userList,
            baseUIState = baseUIState,
            chatViewModel = chatViewModel,
            navigationType = navigationType,
            onDrawerClicked = onDrawerClicked,

            onUserClicked = { selectedItem ->
              val role = baseUIState.userRole

              if (role == UserRole.StandardUser) {
                // ЛОГИКА ЖИЛЬЦА
                Log.d("YkisLog", "NavGraph: [RESIDENT_ACTION] Выбрана квартира: ${selectedItem.address}")

                // 1. Синхронизируем адрес (для счетчиков)
                apartmentViewModel.setAddressId(selectedItem.addressId)

                // Загрузка во вьюмодели запустится сама через LaunchedEffect на экране чата
              } else {
                // ЛОГИКА АДМИНА
                val sysId = when (role) {
                  UserRole.VodokanalUser -> 9999
                  UserRole.YtkeUser -> 9998
                  UserRole.TboUser -> 9997
                  else -> baseUIState.osbbId ?: 0
                }
                Log.d("YkisLog", "NavGraph: [ADMIN_ACTION] Клик по жильцу: ${selectedItem.displayName} | AddrId: ${selectedItem.addressId}")

                // КРИТИЧЕСКИЙ ВОЗВРАТ: Устанавливаем пользователя во вьюмодель,
                // чтобы ChatScreenStateful увидел UID и AddressId
                chatViewModel.openChatWithUser(selectedItem, role, sysId)
              }

              // ОБЩИЙ ПЕРЕХОД
              rootNavController.navigate(ChatScreenDest.route)
            }
          )
        }
        composable(AddApartmentScreen.route) {
          AddApartmentScreenContent(
            viewModel = apartmentViewModel,
            navController = navController,
            onDrawerClicked = onDrawerClicked,
            navigationType = navigationType,
            closeContentDetail = { closeContentDetail() }
          )
        }
        composable(SettingsScreenDest.route) {
          SettingsScreenStateful(
            navigationType = navigationType,
            newSettingsViewModel = newSettingsViewModel,
            navigateToAuthGraph = { rootNavController.cleanNavigateTo(Graph.AUTHENTICATION) },
            onDrawerClick = onDrawerClicked
          )
        }

        composable(MeterScreen.route) {
          MainMeterScreen(
            baseUIState = baseUIState,
            navigationType = navigationType,
            onDrawerClick = onDrawerClicked,
            contentType = contentType,
            displayFeature = displayFeatures,
            viewModel = meterViewModel
          )
        }

        composable(ServiceListScreen.route) {
          MainServiceScreen(
            baseUIState = baseUIState,
            navigationType = navigationType,
            onDrawerClick = onDrawerClicked,
            displayFeature = displayFeatures,
            contentType = contentType,
            viewModel = serviceViewModel,
            navigateToWebView = navigateToWebView
          )
        }

        composable(InfoApartmentScreenDest.route) {
          key(baseUIState.addressId) {
            InfoApartmentScreen(
              contentType = contentType,
              displayFeatures = displayFeatures,
              baseUIState = baseUIState,
              apartmentViewModel = apartmentViewModel,
              deleteApartment = {
                apartmentViewModel.deleteApartment { route ->
                  navController.navigate(route) {
                    popUpTo(InfoApartmentScreenDest.route) { inclusive = true }
                  }
                }
              },
              onDrawerClicked = onDrawerClicked,
              appState = appState,
              navigationType = navigationType
            )
          }
        }
// Внутри NavHost
        composable("service_selector") {
          ServiceSelectorScreen(
            baseUIState = baseUIState,
            chatViewModel = chatViewModel,
            navigationType = navigationType,
            onDrawerClicked = onDrawerClicked,
            // В ApartmentNavGraph внутри ServiceSelectorScreen
            onServiceClick = { service ->
              chatViewModel.setSelectedService(service)

              // Генерируем список ключей для всех квартир жильца под выбранную службу
              val chatKeys = baseUIState.apartments.map { apt ->
                val prefix = service.contentDetail.name
                val osbbId = apt.osmdId ?: 0
                "${prefix}_${osbbId}_${apt.addressId}_${baseUIState.uid}"
              }

              // КРИТИЧНО: Запускаем прослушку превью сообщений
              chatViewModel.subscribeToLastMessages(chatKeys)

              navController.navigate(UserListScreen.route)
            }

          )
        }

        composable(ChatScreenDest.route) {
          // Инициализация сервисного контекста для админов (чтобы ИИ понимал, кто спрашивает)
          LaunchedEffect(baseUIState.userRole) {
            when (baseUIState.userRole) {
              UserRole.YtkeUser -> chatViewModel.setSelectedService(
                TotalServiceDebt(
                  "Теплосеть",
                  ContentDetail.WARM_SERVICE,
                  Icons.Default.HotTub,
                  Color.Red,
                  0.0
                )
              )

              UserRole.VodokanalUser -> chatViewModel.setSelectedService(
                TotalServiceDebt(
                  "Водоканал",
                  ContentDetail.WATER_SERVICE,
                  Icons.Default.WaterDrop,
                  Color.Blue,
                  0.0
                )
              )

              UserRole.TboUser -> chatViewModel.setSelectedService(
                TotalServiceDebt(
                  "Вивіз сміття",
                  ContentDetail.GARBAGE_SERVICE,
                  Icons.Default.Delete,
                  Color.Gray,
                  0.0
                )
              )

              UserRole.OsbbUser -> chatViewModel.trackUserIdentifiersWithRole(
                baseUIState.userRole,
                baseUIState.osbbId ?: 0
              )

              else -> {}
            }
          }

          ChatScreenStateful(
            chatViewModel = chatViewModel,
            baseUIState = baseUIState,
            navigationType = navigationType,
            navController = navController,
          )
        }
      }

    }
  }
}
