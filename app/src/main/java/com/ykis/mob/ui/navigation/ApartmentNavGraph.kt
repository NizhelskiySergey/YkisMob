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
import androidx.compose.runtime.DisposableEffect
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.DisplayFeature
import com.ykis.mob.domain.UserRole
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
import com.ykis.mob.ui.screens.chat.UserListScreen
import com.ykis.mob.ui.screens.family.FamilyListViewModel
import com.ykis.mob.ui.screens.meter.MainMeterScreen
import com.ykis.mob.ui.screens.meter.MeterViewModel
import com.ykis.mob.ui.screens.profile.ProfileScreen
import com.ykis.mob.ui.screens.service.MainServiceScreen
import com.ykis.mob.ui.screens.service.ServiceViewModel
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import com.ykis.mob.ui.screens.settings.SettingsScreenStateful
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
  rootNavController: NavHostController,
  appState: YkisPamAppState,
  onLaunch: () -> Unit,
  onDispose: () -> Unit,
  isRailExpanded: Boolean, // Состояние, управляемое бургером
  onMenuClick: () -> Unit, // Колбэк, который инвертирует isRailExpanded
  navigateToWebView: (String) -> Unit,
) {
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val selectedDestination = navBackStackEntry?.destination?.route ?: InfoApartmentScreenDest.route
  val baseUIState by apartmentViewModel.uiState.collectAsStateWithLifecycle()

  // Анимация ширины теперь напрямую зависит от isRailExpanded
  // Это позволит бургеру сворачивать и разворачивать панель
  val railWidth by animateDpAsState(
    targetValue = if (isRailExpanded) 280.dp else 80.dp,
    animationSpec = tween(400),
    label = "RailWidth"
  )
// Внутри MainApartmentScreen
  val firstDestination = remember(baseUIState.userRole, baseUIState.apartments) {
    when (baseUIState.userRole) {
      // Сервисные админы (используем имена из твоего Enum UserRole)
      UserRole.VodokanalUser,
      UserRole.YtkeUser,
      UserRole.TboUser,
      UserRole.OsbbUser -> UserListScreen.route // Или ChatScreen, если решили сразу в чат

      UserRole.StandardUser -> {
        if (baseUIState.apartments.isEmpty()) AddApartmentScreen.route
        else InfoApartmentScreenDest.route
      }

      else -> InfoApartmentScreenDest.route
    }
  }




  DisposableEffect(Unit) {
    onLaunch()
    apartmentViewModel.observeUserProfile()
    onDispose { onDispose() }
  }

  val movableApartmentNavGraph = remember(baseUIState, contentType, navigationType) {
    movableContentOf {
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
//        firstDestination = if (baseUIState.apartments.isNotEmpty()) InfoApartmentScreen.route else AddApartmentScreen.route,
        firstDestination = firstDestination,
        meterViewModel = meterViewModel,
        serviceViewModel = serviceViewModel,
        chatViewModel = chatViewModel,
        closeContentDetail = {
          meterViewModel.closeContentDetail()
          serviceViewModel.closeContentDetail()
        },
        navigateToWebView = navigateToWebView
      )
    }
  }

  if (navigationType == NavigationType.BOTTOM_NAVIGATION) {
    // --- ТЕЛЕФОН (Drawer + BottomBar) ---
    ModalNavigationDrawer(
      drawerState = drawerState,
      drawerContent = {
        ModalNavigationDrawerContent(
          baseUIState = baseUIState,
          selectedDestination = selectedDestination,
          chatViewModel = chatViewModel, // Передаем для Badge в Drawer
          navigateToDestination = { dest ->
            coroutineScope.launch {
              drawerState.close()
              navController.navigateWithPopUp(dest, InfoApartmentScreenDest.route)
            }
          },
          onMenuClick = { coroutineScope.launch { drawerState.close() } },
          navigateToApartment = { id ->
            apartmentViewModel.setAddressId(id)
            coroutineScope.launch { drawerState.close() }
            navController.navigate(InfoApartmentScreenDest.route) {
              popUpTo(0) { inclusive = true }
              launchSingleTop = true
            }
          },
          isApartmentsEmpty = baseUIState.apartments.isEmpty()
        )
      }
    ) {
      Scaffold(
        bottomBar = {
          if (baseUIState.apartments.isNotEmpty()) {
            BottomNavigationBar(
              selectedDestination = selectedDestination,
              chatViewModel = chatViewModel, // ИСПРАВЛЕНО: Передаем для Badge в BottomBar
              onClick = { dest ->
                navController.navigateWithPopUp(dest, InfoApartmentScreenDest.route)
              }
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
    // --- ПЛАНШЕТ (Navigation Rail) ---
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Row(modifier = Modifier.fillMaxSize()) {
        ApartmentNavigationRail(
          modifier = Modifier.width(railWidth),
          baseUIState = baseUIState,
          selectedDestination = selectedDestination,
          chatViewModel = chatViewModel, // Передаем для Badge в Rail
          isRailExpanded = isRailExpanded,
          onMenuClick = onMenuClick,
          navigateToDestination = { dest ->
            navController.navigateWithPopUp(dest, InfoApartmentScreenDest.route)
          },
          navigateToApartment = { id ->
            apartmentViewModel.setAddressId(id)
            // Для планшета (Rail) обычно шторку закрывать не нужно, но логику сохраняем
            navController.navigate(InfoApartmentScreenDest.route) {
              popUpTo(InfoApartmentScreenDest.route) { inclusive = true }
            }
          },
          isApartmentsEmpty = baseUIState.apartments.isEmpty(),
          railWidth = railWidth,
          maxApartmentListHeight = 220.dp,
        )

        VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
        composable(ProfileScreenDest.route) {

          ProfileScreen(
            navigationType = navigationType,
            onDrawerClicked = onDrawerClicked,
            navigateToSettings = {
              navController.navigate(SettingsScreenDest.route)
            }
          )
        }
        composable(UserListScreen.route) {
          // 1. Автоматический запуск отслеживания чатов для админа при входе на экран
          LaunchedEffect(baseUIState.userRole, baseUIState.osbbId) {
            val role = baseUIState.userRole
            val osbbId = baseUIState.osbbId ?: 0

            if (role != UserRole.StandardUser) {
              Log.d("YkisLog", "NavGraph: Admin ($role) tracking for OSBB: $osbbId")
              chatViewModel.trackUserIdentifiersWithRole(role, osbbId)
            }
          }

          UserListScreen(
            userList = userList,
            baseUIState = baseUIState,
            chatViewModel = chatViewModel,
            navigationType = navigationType,
            onDrawerClicked = onDrawerClicked,

            // КЛИК АДМИНА: Используем новую функцию openChatWithUser
            onUserClicked = { resident ->
              // Определяем правильный osbbId для формирования пути chatId
              val effectiveOsbbId = when (baseUIState.userRole) {
                UserRole.OsbbUser -> baseUIState.osbbId ?: 0
                // Для сервисных служб (Водоканал и т.д.) osbbId в пути не участвует,
                // но метод требует Int. Передаем 0 или то, что ожидает getChatPath
                else -> 0
              }

              chatViewModel.openChatWithUser(
                user = resident,
                currentRole = baseUIState.userRole,
                currentOsbbId = effectiveOsbbId
              )
              rootNavController.navigate(ChatScreenDest.route)
            },


            // КЛИК ЖИЛЬЦА: Передаем addressId текущей выбранной квартиры
            onServiceClick = { service ->
              chatViewModel.setSelectedService(service)
              chatViewModel.readFromDatabase(
                role = baseUIState.userRole,
                senderUid = baseUIState.uid ?: "",
                osbbId = baseUIState.osbbId ?: 0,
                addressId = baseUIState.addressId // Передаем ID текущей квартиры жильца
              )
              rootNavController.navigate(ChatScreenDest.route)
            }
          )
        }


        composable(AddApartmentScreen.route) {
          AddApartmentScreenContent(
            viewModel = apartmentViewModel,
            navController = navController,
            canNavigateBack = navController.previousBackStackEntry != null,
            onDrawerClicked = onDrawerClicked,
            navigationType = navigationType,
            closeContentDetail = {
              closeContentDetail()
            }
          )
        }

        composable(SettingsScreenDest.route) {
          SettingsScreenStateful(
            navigationType = navigationType,
            navigateToAuthGraph = {
              rootNavController.cleanNavigateTo(Graph.AUTHENTICATION)
            },
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

        composable(InfoApartmentScreenDest.route){
          key(baseUIState.addressId) {
            InfoApartmentScreen(
              contentType = contentType,
              displayFeatures = displayFeatures,
              baseUIState = baseUIState,
              apartmentViewModel = apartmentViewModel,
              deleteApartment = {
                // ПЕРЕДАЕМ ЛОГИКУ НАВИГАЦИИ ВО VIEWMODEL
                apartmentViewModel.deleteApartment { route ->
                  navController.navigate(route) {
                    // Очищаем текущий экран из стека, чтобы нельзя было вернуться назад к удаленной квартире
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
        composable(ChatScreenDest.route) {
          // Инициализируем данные чата при входе для админов
          LaunchedEffect(baseUIState.userRole, baseUIState.osbbId) {
            when (baseUIState.userRole) {
              // Теплосеть (9997)
              UserRole.YtkeUser -> {
                chatViewModel.setSelectedService(
                  TotalServiceDebt(
                    name = "Теплосеть",
                    contentDetail = ContentDetail.WARM_SERVICE,
                    icon = Icons.Default.HotTub, // Любая иконка-заглушка
                    color = Color.Red,
                    debt = 0.0
                  )
                )
              }

              // Водоканал (9998)
              UserRole.VodokanalUser -> {
                chatViewModel.setSelectedService(
                  TotalServiceDebt(
                    name = "Водоканал",
                    contentDetail = ContentDetail.WATER_SERVICE,
                    icon = Icons.Default.WaterDrop,
                    color = Color.Blue,
                    debt = 0.0
                  )
                )
              }

              // Вывоз мусора (9999)
              UserRole.TboUser -> {
                chatViewModel.setSelectedService(
                  TotalServiceDebt(
                    name = "Вывоз мусора",
                    contentDetail = ContentDetail.GARBAGE_SERVICE,
                    icon = Icons.Default.Delete,
                    color = Color.Gray,
                    debt = 0.0
                  )
                )
              }

              // Админ ОСББ
              UserRole.OsbbUser -> {
                if (baseUIState.osbbId != null) {
                  chatViewModel.trackUserIdentifiersWithRole(
                    baseUIState.userRole,
                    baseUIState.osbbId
                  )
                }
              }

              else -> { /* Для StandardUser выборка уже сделана при клике */
              }
            }
          }

          // Вызываем экран (здесь используй Stateful версию или передавай все 10 параметров)
          ChatScreenStateful(
            chatViewModel = chatViewModel,
            baseUIState = baseUIState,
            navController = navController,

            )
        }


      }
    }
  }
}
