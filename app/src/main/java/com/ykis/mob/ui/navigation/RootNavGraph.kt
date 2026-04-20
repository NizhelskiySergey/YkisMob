package com.ykis.mob.ui.navigation

import android.util.Log
import android.net.Uri
import android.net.http.SslCertificate.restoreState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.core.snackbar.SnackbarMessage
import com.ykis.mob.data.cache.preferences.PreferenceRepository
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.service.repo.ConfigurationService
import com.ykis.mob.ui.rememberAppState
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.auth.sign_up.SignUpViewModel
import com.ykis.mob.ui.screens.auth.sign_up.TermsAndConditionScreen
import com.ykis.mob.ui.screens.chat.CameraScreen
import com.ykis.mob.ui.screens.chat.ChatScreenContent
import com.ykis.mob.ui.screens.chat.ChatScreenStateful
import com.ykis.mob.ui.screens.chat.ChatViewModel
import com.ykis.mob.ui.screens.chat.ImageDetailScreen
import com.ykis.mob.ui.screens.chat.SendImageScreen
import com.ykis.mob.ui.screens.chat.UserEntity
import com.ykis.mob.ui.screens.meter.MeterViewModel
import com.ykis.mob.ui.screens.service.ServiceViewModel
import com.ykis.mob.ui.screens.service.payment.choice.WebView
import com.ykis.mob.ui.screens.settings.NewSettingsViewModel
import io.ktor.client.utils.EmptyContent.contentType
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
  newSettingsViewModel: NewSettingsViewModel =koinViewModel (),
  meterViewModel: MeterViewModel = koinViewModel(),
  serviceViewModel: ServiceViewModel= koinViewModel (),
  contentType: ContentType,
  displayFeatures: List<DisplayFeature>,
  navigationType: NavigationType,
  initialChatId: String? = null // ПРИНИМАЕМ ID ЧАТА
) {
  val appState = rememberAppState()
  val scope = rememberCoroutineScope()
  var isMainScreen by rememberSaveable { mutableStateOf(false) }

  // Инъекция репозитория
  val preferenceRepository: PreferenceRepository = koinInject()
  val configurationService: ConfigurationService = koinInject()
// Достаем текст (он уже должен быть загружен в init приложения или через fetchAndActivate)
  val termsText = remember { configurationService.agreementText}
  // Инициализируем false, чтобы LaunchedEffect сработал на проверку
  var isAgreed by remember { mutableStateOf(false) }

  // Состояние бокового меню (Rail)
  var isRailExpanded by rememberSaveable {
    mutableStateOf(navigationType != NavigationType.BOTTOM_NAVIGATION)
  }

  val selectedUser by chatViewModel.selectedUser.collectAsStateWithLifecycle()
  val baseUIState by apartmentViewModel.uiState.collectAsStateWithLifecycle()
  val selectedImageUri by chatViewModel.selectedImageUri.collectAsStateWithLifecycle()
  // 1. ПЕРВИЧНАЯ ПРОВЕРКА СОГЛАСИЯ ПРИ ЗАПУСКЕ
  LaunchedEffect(Unit) {
    isAgreed = preferenceRepository.isUserAgreed()
  }


  // --- [ЗОЛОТОЙ ФОНД] ЛОГИКИ ПЕРЕХОДОВ ---
  LaunchedEffect(isAgreed, initialChatId, baseUIState.uid, baseUIState.mainLoading) {
    val currentRoute = navController.currentDestination?.route
    val isUserLoggedIn = baseUIState.uid != null

    // ЭТАП 0: Проверка соглашения (Самый высокий приоритет)
    if (!isAgreed) {
      if (currentRoute != Graph.TERMS_CONDITION) {
        Log.d("YkisLog", "Nav: Пользователь не принял условия. Переход на Graph.TERMS_CONDITION")
        navController.navigate(Graph.TERMS_CONDITION) {
          popUpTo(0) { inclusive = true }
        }
      }
      return@LaunchedEffect // Остановка, дальше проверки не идут
    }

    // ЭТАП 1: Если пользователь ВЫШЕЛ (uid == null) и мы не загружаемся
    if (!isUserLoggedIn && !baseUIState.mainLoading) {
      if (currentRoute != null && !currentRoute.contains(Graph.AUTHENTICATION)) {
        Log.d("YkisLog", "Nav: Юзер вышел. Переход на AUTHENTICATION")
        cleanNavigateTo(navController, Graph.AUTHENTICATION)
      }
      return@LaunchedEffect
    }

    // ЭТАП 2: Обработка PUSH (только для авторизованных)
    if (isUserLoggedIn && !initialChatId.isNullOrEmpty()) {
      if (currentRoute != ChatScreenDest.route) {
        if (baseUIState.userRole != UserRole.StandardUser) {
          chatViewModel.fetchAndSelectUser(initialChatId) {
            navController.navigate(ChatScreenDest.route) { launchSingleTop = true }
          }
        } else {
          navController.navigate(ChatScreenDest.route) { launchSingleTop = true }
        }
      }
      return@LaunchedEffect
    }

    // ЭТАП 3: Автоматический вход в приложение (Graph.APARTMENT)
    if (isUserLoggedIn && !baseUIState.mainLoading) {
      if (currentRoute == null || currentRoute.contains(Graph.AUTHENTICATION) || currentRoute == Graph.TERMS_CONDITION) {
        Log.d("YkisLog", "Nav: Авто-вход. Переход на APARTMENT")
        cleanNavigateTo(navController, Graph.APARTMENT)
      }
    }
  }

  // Универсальное вычисление chatUid
  val chatUid = remember(baseUIState.userRole, baseUIState.osbbId, selectedUser, baseUIState.uid) {
    if (baseUIState.uid == null) return@remember ""
    when (baseUIState.userRole) {
      UserRole.YtkeUser -> "9997"
      UserRole.VodokanalUser -> "9998"
      UserRole.TboUser -> "9999"
      UserRole.OsbbUser -> selectedUser?.uid ?: ""
      UserRole.StandardUser -> baseUIState.uid.toString()
      else -> selectedUser?.uid ?: ""
    }
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
        startDestination = remember { apartmentViewModel.onAppStart() },
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
      ) {
        // Внутри RootNavGraph в блоке NavHost
        // Внутри RootNavGraph -> NavHost

        composable(route = Graph.TERMS_CONDITION) {
          TermsAndConditionScreen(
            termsText = termsText.ifBlank { stringResource(R.string.agreement_text) }, // Fallback на локальный ресурс
            onAccept = {
              scope.launch {
                preferenceRepository.setAgreement(true)
                isAgreed = true
                val target = if (baseUIState.uid != null) Graph.APARTMENT else Graph.AUTHENTICATION
                navController.navigate(target) { popUpTo(Graph.TERMS_CONDITION) { inclusive = true } }
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
            navigationType= navigationType,
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
                senderAddress = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.address else selectedUser?.address ?: "",
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
              chatViewModel.analyzePhotoWithGemini(uri, context,baseUIState.address)

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
