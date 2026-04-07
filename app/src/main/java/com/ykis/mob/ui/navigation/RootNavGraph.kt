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
import com.ykis.mob.ui.screens.service.payment.choice.WebView
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
  contentType: ContentType,
  displayFeatures: List<DisplayFeature>,
  navigationType: NavigationType,
) {
  val appState = rememberAppState()
  var isMainScreen by rememberSaveable { mutableStateOf(false) }

  // Состояние бокового меню (Rail)
  var isRailExpanded by rememberSaveable {
    mutableStateOf(navigationType != NavigationType.BOTTOM_NAVIGATION)
  }

  val selectedUser by chatViewModel.selectedUser.collectAsStateWithLifecycle()
  val baseUIState by apartmentViewModel.uiState.collectAsStateWithLifecycle()
  val selectedImageUri by chatViewModel.selectedImageUri.collectAsStateWithLifecycle()

  // ИСПРАВЛЕНО: Универсальное вычисление chatUid для всех ролей
  val chatUid = remember(baseUIState.userRole, baseUIState.osbbId, selectedUser) {
    when (baseUIState.userRole) {
      UserRole.YtkeUser -> "9997"
      UserRole.VodokanalUser -> "9998"
      UserRole.TboUser -> "9999"
      // Добавляем безопасный вызов ?.uid
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
        startDestination = apartmentViewModel.onAppStart(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
      ) {
        // Граф авторизации
        authNavGraph(
          navController = navController,
          signUpViewModel = signUpViewModel
        )

        // Главный экран (Квартиры/Услуги)
        composable(route = Graph.APARTMENT) {
          MainApartmentScreen(
            contentType = contentType,
            navigationType = navigationType,
            displayFeatures = displayFeatures,
            rootNavController = navController,
            appState = appState,
            onLaunch = { isMainScreen = true },
            onDispose = { isMainScreen = false },
            isRailExpanded = isRailExpanded,
            onMenuClick = { isRailExpanded = !isRailExpanded },
            navigateToWebView = { uri -> navController.navigateToWebView(uri) },
            chatViewModel = chatViewModel,
            apartmentViewModel = apartmentViewModel,
          )
        }

        // Встроенный браузер
        composable(
          route = WebViewScreenDest.routeWithArgs,
          arguments = WebViewScreenDest.arguments
        ) { navBackStackEntry ->
          val uri = navBackStackEntry.arguments?.getString(WebViewScreenDest.link)
          WebView(uri = uri.toString())
        }

        // ИСПРАВЛЕНО: Вызов ChatScreen (имя функции должно совпадать с твоим файлом)
        composable(ChatScreenDest.route) {
          ChatScreenContent(
            modifier = Modifier,                        // Добавь это!
            userEntity = selectedUser ?: UserEntity(),  // 1
            chatViewModel = chatViewModel,              // 2
            baseUIState = baseUIState,                  // 3
            navigateBack = { navController.navigateUp() }, // 4
            navigateToSendImageScreen = { navController.navigate(SendImageScreenDest.route) }, // 5
            chatUid = chatUid,                          // 6
            navigateToCameraScreen = { navController.navigate(CameraScreenDest.route) }, // 7
            navigateToImageDetailScreen = { message ->  // 8
              chatViewModel.setSelectedMessage(message)
              navController.navigate(ImageDetailScreenDest.route)
            }
          )
        }



        // Отправка фото
        composable(SendImageScreenDest.route) {
          val messageText by chatViewModel.messageText.collectAsStateWithLifecycle()
          val isLoadingAfterSending by chatViewModel.isLoadingAfterSending.collectAsStateWithLifecycle()
          val context = androidx.compose.ui.platform.LocalContext.current

          // 1. Вычисляем ID организации
          val currentOsbbId = when (baseUIState.userRole) {
            UserRole.YtkeUser -> 9997
            UserRole.VodokanalUser -> 9998
            UserRole.TboUser -> 9999
            UserRole.OsbbUser -> baseUIState.osbbId
            else -> baseUIState.osmdId
          }

          // 2. Вычисляем ID квартиры (из профиля жильца или текущего стейта)
          val currentAddressId = if (baseUIState.userRole == UserRole.StandardUser) {
            baseUIState.addressId
          } else {
            selectedUser.addressId
          }

          // 3. Формируем имя отправителя для заголовка уведомления
          val finalDisplayName = if (baseUIState.userRole == UserRole.StandardUser) {
            // Жилец отправляет "Адрес | Фамилия" (из displayName)
            baseUIState.displayName ?: "Жилец"
          } else {
            // Админ отправляет название своей службы
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
            onSent = {
              chatViewModel.uploadPhotoAndSendMessage(
                context = context,
                chatUid = chatUid,
                senderUid = baseUIState.uid.toString(),
                senderDisplayedName = finalDisplayName,
                senderLogoUrl = baseUIState.photoUrl,
                role = baseUIState.userRole,
                senderAddress = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.address else selectedUser.address,
                addressId = currentAddressId, // ПЕРЕДАЕМ ID КВАРТИРЫ
                osbbId = currentOsbbId,
                recipientTokens = selectedUser.tokens,
                onComplete = {
                  // Возврат в чат, закрывая камеру и экран отправки
                  navController.popBackStack(ChatScreenDest.route, inclusive = false)
                }
              )
            },
            isLoadingAfterSending = isLoadingAfterSending,
            chatViewModel = chatViewModel
          )
        }


        composable(CameraScreenDest.route) {
          CameraScreen(
            navController = navController,
            setImageUri = { chatViewModel.setSelectedImageUri(it) }
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
