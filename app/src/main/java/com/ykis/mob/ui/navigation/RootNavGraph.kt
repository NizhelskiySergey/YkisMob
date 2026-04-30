package com.ykis.mob.ui.navigation

import android.util.Log
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.DisplayFeature
import com.ykis.mob.R
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.ui.rememberAppState
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.auth.sign_up.SignUpViewModel
import com.ykis.mob.ui.screens.auth.sign_up.TermsAndConditionScreen
import com.ykis.mob.ui.screens.chat.CameraScreen
import com.ykis.mob.ui.screens.chat.ChatScreenContent
import com.ykis.mob.ui.screens.chat.ChatViewModel
import com.ykis.mob.ui.screens.chat.ImageDetailScreen
import com.ykis.mob.ui.screens.chat.SendImageScreen
import com.ykis.mob.ui.screens.chat.UserEntity
import com.ykis.mob.ui.screens.meter.MeterViewModel
import com.ykis.mob.ui.screens.service.ServiceViewModel
import com.ykis.mob.ui.screens.service.payment.choice.WebView
import com.ykis.mob.ui.screens.settings.NewSettingsViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

object Graph {
  const val TERMS_CONDITION = "terms_graph" // Новый этап
  const val AUTHENTICATION = "auth_graph"
  const val APARTMENT = "apartment_graph"
}

@Composable
fun RootNavGraph(
  modifier: Modifier = Modifier,
  navController: NavHostController = rememberNavController(),
  chatViewModel: ChatViewModel = koinViewModel(),
  apartmentViewModel: ApartmentViewModel = koinViewModel(),
  signUpViewModel: SignUpViewModel = koinViewModel(),
  newSettingsViewModel: NewSettingsViewModel = koinViewModel(),
  meterViewModel: MeterViewModel = koinViewModel(),
  serviceViewModel: ServiceViewModel = koinViewModel(),
  firebaseService: FirebaseService = koinInject(),
  contentType: ContentType,
  displayFeatures: List<DisplayFeature>,
  navigationType: NavigationType,
  initialChatId: String? = null // ПРИНИМАЕМ ID ЧАТА
) {
  val appState = rememberAppState()
  val scope = rememberCoroutineScope()
  var isMainScreen by rememberSaveable { mutableStateOf(false) }

  // Инъекция репозитория
// Достаем текст (он уже должен быть загружен в init приложения или через fetchAndActivate)
  val termsText = remember { firebaseService.agreementText }
  // Инициализируем false, чтобы LaunchedEffect сработал на проверку
  var isAgreed by remember { mutableStateOf(false) }

  // Состояние бокового меню (Rail)
  var isRailExpanded by rememberSaveable {
    mutableStateOf(navigationType != NavigationType.BOTTOM_NAVIGATION)
  }

  val selectedUser by chatViewModel.selectedUser.collectAsStateWithLifecycle()
  val baseUIState by apartmentViewModel.uiState.collectAsStateWithLifecycle()
  val selectedImageUri by chatViewModel.selectedImageUri.collectAsStateWithLifecycle()
  val isSettingsLoading by newSettingsViewModel.loading.collectAsStateWithLifecycle()
  val currentFirebaseUid = firebaseService.uid
  // 1. ПЕРВИЧНАЯ ПРОВЕРКА СОГЛАСИЯ ПРИ ЗАПУСКЕ
  LaunchedEffect(Unit) {
    val agreedFromCache = firebaseService.isUserAgreed() // Теперь ошибки нет!
    Log.d("YkisLog", "RootNavGraph: Чтение из кэша is_agreed = $agreedFromCache")
    isAgreed = agreedFromCache
  }


  // --- [ЗОЛОТОЙ ФОНД] ЛОГИКИ ПЕРЕХОДОВ ---
  LaunchedEffect(isAgreed, currentFirebaseUid) {
    val methodName = "RootNavGraph.LaunchedEffect"
    val currentRoute = navController.currentDestination?.route

    // 1. Если не принял условия — только TERMS
    if (!isAgreed) {
      if (currentRoute != Graph.TERMS_CONDITION) {
        navController.navigate(Graph.TERMS_CONDITION) { popUpTo(0) { inclusive = true } }
      }
      return@LaunchedEffect
    }

    // 2. Если принял условия — решаем: ЛОГИН или ГЛАВНАЯ
    if (isAgreed) {
      if (currentFirebaseUid == null) {
        // СЛУЧАЙ А: Сессии нет (Новичок или разлогинился) -> ТОЛЬКО ЛОГИН
        if (currentRoute == null || !currentRoute.contains(Graph.AUTHENTICATION)) {
          Log.d("YkisLog", "$methodName: [NAV] -> AUTHENTICATION (Сессии нет)")
          navController.navigate(Graph.AUTHENTICATION) {
            popUpTo(Graph.TERMS_CONDITION) { inclusive = true }
          }
        }
      } else {
        // СЛУЧАЙ Б: Сессия ЕСТЬ (Пользователь уже заходил раньше) -> НА ГЛАВНУЮ
        if (currentRoute == null || currentRoute.contains(Graph.AUTHENTICATION) || currentRoute == Graph.TERMS_CONDITION) {
          Log.d("YkisLog", "$methodName: [NAV] -> APARTMENT (Сессия активна: $currentFirebaseUid)")
          // Перед переходом убедимся, что данные подгружаются
          if (baseUIState.apartments.isEmpty()) {
            apartmentViewModel.observeUserProfile()
          }
          cleanNavigateTo(navController, Graph.APARTMENT)
        }
      }
    }
  }


  // Универсальное вычисление chatUid
  val chatUid = remember(baseUIState.userRole, baseUIState.apartment, selectedUser, baseUIState.uid) {
    val methodName = "RootNavGraph.chatUid"
    val userRole = baseUIState.userRole
    val apartment = baseUIState.apartment
    val myUid = baseUIState.uid

    val resultUid = when (userRole) {
      // --- ДЛЯ ГОРСЛУЖБ (Водоканал и др.) ---
      // Мы должны писать ЖИТЕЛЮ выбранной квартиры
      UserRole.VodokanalUser, UserRole.YtkeUser, UserRole.TboUser -> {
        apartment.uid ?: ""
      }

      // --- ДЛЯ АДМИНА ОСББ ---
      // Он пишет конкретному человеку, выбранному из списка жильцов
      UserRole.OsbbUser -> {
        selectedUser?.uid ?: ""
      }

      // --- ДЛЯ ЖИТЕЛЯ ---
      UserRole.StandardUser -> {
        myUid ?: ""
      }

      else -> ""
    }

    // ПОДРОБНЫЙ ЛОГ ДЛЯ ТЕБЯ
    Log.d("YkisLog", "$methodName: [CALC] " +
      "Role: $userRole | " +
      "MyUID: $myUid | " +
      "ApartmentUID: ${apartment.uid} | " +
      "SelectedUserUID: ${selectedUser?.uid} | " +
      "RESULT_CHAT_UID: $resultUid")

    resultUid
  }




  Scaffold(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    snackbarHost = {
      SnackbarHost(hostState = appState.snackbarHostState) { data ->
        Snackbar(data)
      }
    },
  ) { paddingValues ->
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(bottom = paddingValues.calculateBottomPadding() * 0)
    ) {
      NavHost(
        modifier = Modifier.fillMaxSize(),
        navController = navController,
        startDestination = when {
          !isAgreed -> Graph.TERMS_CONDITION             // 1. Сначала лицензия
          firebaseService.currentUser == null -> Graph.AUTHENTICATION // 2. Потом логин
          else -> Graph.APARTMENT                        // 3. Если всё есть — в приложение
        },
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
      ) {
        // Внутри RootNavGraph в блоке NavHost
        // Внутри RootNavGraph -> NavHost

        composable(route = Graph.TERMS_CONDITION) {
          TermsAndConditionScreen(
            termsText = termsText,
            firebaseService = firebaseService,
            onAccept = {
              scope.launch {
                firebaseService.setAgreement(true)
                isAgreed = true
                Log.d("YkisLog", "Terms: Согласие получено. Уходим на AUTH")
                // Явный переход на логин сразу после клика
                navController.navigate(Graph.AUTHENTICATION) {
                  popUpTo(Graph.TERMS_CONDITION) { inclusive = true }
                }
              }
            }
          )
        }


        // --- ГРАФ АВТОРИЗАЦИИ ---
        authNavGraph(
          navController = navController,
          signUpViewModel = signUpViewModel
        )

        // --- ГЛАВНЫЙ ЭКРАН ---
        composable(route = Graph.APARTMENT) {
          MainApartmentScreen(
            contentType = contentType,
            navigationType = navigationType,
            displayFeatures = displayFeatures,
            rootNavController = navController,
            apartmentViewModel = apartmentViewModel,
            meterViewModel = meterViewModel,
            serviceViewModel = serviceViewModel,
            chatViewModel = chatViewModel,
            firebaseService = firebaseService,
            appState = appState,
            onLaunch = { isMainScreen = true },
            onDispose = { isMainScreen = false },
            isRailExpanded = isRailExpanded,
            onMenuClick = { isRailExpanded = !isRailExpanded },
            navigateToWebView = { uri -> navController.navigateToWebView(uri) },
          )
        }


        // --- ВСТРОЕННЫЙ БРАУЗЕР ---
        composable(
          route = WebViewScreenDest.routeWithArgs,
          arguments = WebViewScreenDest.arguments
        ) { navBackStackEntry ->
          val uri = navBackStackEntry.arguments?.getString(WebViewScreenDest.link)
          WebView(uri = uri.toString())
        }

        // --- ЭКРАН ЧАТА ---
        composable(ChatScreenDest.route) {
          ChatScreenContent(
            modifier = Modifier,
            userEntity = selectedUser ?: UserEntity(),
            chatViewModel = chatViewModel,
            baseUIState = baseUIState,
            navigationType = navigationType,
            navigateBack = { navController.navigateUp() },
            navigateToSendImageScreen = { navController.navigate(SendImageScreenDest.route) },
            chatUid = chatUid,
            navigateToCameraScreen = { navController.navigate(CameraScreenDest.route) },
            navigateToImageDetailScreen = { message ->
              chatViewModel.setSelectedMessage(message)
              navController.navigate(ImageDetailScreenDest.route)
            }
          )
        }

        // --- ОТПРАВКА ФОТО ---
        composable(SendImageScreenDest.route) {
          val messageText by chatViewModel.messageText.collectAsStateWithLifecycle()
          val isLoadingAfterSending by chatViewModel.isLoadingAfterSending.collectAsStateWithLifecycle()
          val context = androidx.compose.ui.platform.LocalContext.current

          val currentOsbbId = when (baseUIState.userRole) {
            UserRole.YtkeUser -> 9997
            UserRole.VodokanalUser -> 9998
            UserRole.TboUser -> 9999
            UserRole.OsbbUser -> baseUIState.osbbId
            else -> baseUIState.osmdId
          }

          val currentAddressId = if (baseUIState.userRole == UserRole.StandardUser) {
            baseUIState.addressId
          } else {
            selectedUser?.addressId ?: 0
          }

          val finalDisplayName = if (baseUIState.userRole == UserRole.StandardUser) {
            baseUIState.displayName ?: "Жилец"
          } else {
            when (baseUIState.userRole) {
              UserRole.VodokanalUser -> "Водоканал"
              UserRole.YtkeUser -> "Теплосеть"
              UserRole.TboUser -> "Вывоз мусора"
              UserRole.OsbbUser -> "ОСББ"
              else -> "Диспетчер"
            }
          }

          SendImageScreen(
            imageUri = selectedImageUri,
            messageText = messageText,
            onMessageTextChanged = { chatViewModel.onMessageTextChanged(it) },
            navigateBack = { navController.navigateUp() },
            address = baseUIState.address,
            onSent = {
              chatViewModel.uploadFileAndSendMessage(
                context = context,
                chatUid = chatUid,
                senderUid = baseUIState.uid.toString(),
                senderDisplayedName = finalDisplayName,
                senderLogoUrl = baseUIState.photoUrl,
                role = baseUIState.userRole,
                senderAddress = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.address else selectedUser?.address
                  ?: "",
                addressId = currentAddressId,
                osbbId = currentOsbbId,
                recipientTokens = selectedUser?.tokens ?: emptyList(),
                onComplete = {
                  navController.popBackStack(ChatScreenDest.route, inclusive = false)
                }
              )
            },
            isLoadingAfterSending = isLoadingAfterSending,
            chatViewModel = chatViewModel
          )
        }

        composable(CameraScreenDest.route) {
          val context = LocalContext.current // Берем контекст
          CameraScreen(
            navController = navController,
            setImageUri = { uri ->
              // 1. Сохраняем фото во вьюмодели
              chatViewModel.setSelectedImageUri(uri)

              // 2. ЗАПУСКАЕМ AI АВТОМАТИЧЕСКИ
              Log.d("YkisLog", "Camera: Авто-запуск AI для сделанного фото")
              chatViewModel.analyzePhotoWithGemini(uri, context, baseUIState.address)

              // 3. Навигация на экран подтверждения уже обычно зашита в CameraScreen,
              // но если нет — добавь navController.navigate(SendImageScreenDest.route)
            }
          )
        }


        composable(ImageDetailScreenDest.route) {
          val selectedMessage by chatViewModel.selectedMessage.collectAsStateWithLifecycle()
          ImageDetailScreen(
            navigateUp = { navController.navigateUp() },
            messageEntity = selectedMessage
          )
        }
      }
    }
  }
}


/**
 * Расширение для навигации в WebView
 */
private fun NavHostController.navigateToWebView(uri: String) {
  this.navigate("${WebViewScreenDest.route}/$uri")
}

fun cleanNavigateTo(navController: NavController, route: String) {
  Log.d("YkisLog", "Nav: [CLEAN_START] Полная очистка стека и переход на $route")
  navController.navigate(route) {
    // Мы чистим всё до основания, чтобы убить все старые ViewModel и их стейты
    popUpTo(0) { inclusive = true }
    launchSingleTop = true
    restoreState = false // КРИТИЧНО: не восстанавливаем старый хлам
  }
}
