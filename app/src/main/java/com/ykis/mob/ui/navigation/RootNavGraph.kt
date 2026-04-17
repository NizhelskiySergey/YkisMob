package com.ykis.mob.ui.navigation

import android.util.Log
import android.net.Uri
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.DisplayFeature
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.rememberAppState
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.auth.sign_up.SignUpViewModel
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
import org.koin.compose.viewmodel.koinViewModel

object Graph {
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
  var isMainScreen by rememberSaveable {
    mutableStateOf(false)
  }
  // Состояние бокового меню (Rail)
  var isRailExpanded by rememberSaveable {
    mutableStateOf(navigationType != NavigationType.BOTTOM_NAVIGATION)
  }

  val selectedUser by chatViewModel.selectedUser.collectAsStateWithLifecycle()
  val baseUIState by apartmentViewModel.uiState.collectAsStateWithLifecycle()
  val selectedImageUri by chatViewModel.selectedImageUri.collectAsStateWithLifecycle()

  // --- [СТАБИЛЬНАЯ ВЕРСИЯ] ЛОГИКИ ПЕРЕХОДОВ ---
  LaunchedEffect(initialChatId, baseUIState.uid, baseUIState.mainLoading) {
    val currentRoute = navController.currentDestination?.route
    val isUserLoggedIn = baseUIState.uid != null

    // 1. Если пользователь ВЫШЕЛ (uid == null) и мы не загружаемся
    if (!isUserLoggedIn && !baseUIState.mainLoading) {
      // Если мы не в графе авторизации — принудительно уходим туда
      if (currentRoute != null && !currentRoute.contains(Graph.AUTHENTICATION)) {
        navController.navigate(Graph.AUTHENTICATION) {
          popUpTo(0) { inclusive = true }
        }
      }
      return@LaunchedEffect
    }

    // 2. Обработка PUSH (только для авторизованных)
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

    // 3. Автоматический вход в приложение (Graph.APARTMENT)
    // Срабатывает только если: Юзер есть, Мы не в процессе загрузки профиля,
    // и мы либо на старте, либо еще в графе авторизации
    if (isUserLoggedIn && !baseUIState.mainLoading) {
      if (currentRoute == null || currentRoute.contains(Graph.AUTHENTICATION)) {
        navController.navigate(Graph.APARTMENT) {
          popUpTo(0) { inclusive = true }
          launchSingleTop = true
        }
      }
    }
  }

  // Универсальное вычисление chatUid
  val chatUid = remember(baseUIState.userRole, baseUIState.osbbId, selectedUser, baseUIState.uid) {
    if (baseUIState.uid == null) return@remember "" // Защита от NPE при выходе
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
