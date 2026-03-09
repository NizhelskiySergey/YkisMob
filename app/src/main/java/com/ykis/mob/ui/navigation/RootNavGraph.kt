package com.ykis.mob.ui.navigation

import android.util.Log
import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.DisplayFeature
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.GlobalLoadingBarrier
import com.ykis.mob.ui.rememberAppState
import com.ykis.mob.ui.screens.appartment.ApartmentViewModel
import com.ykis.mob.ui.screens.auth.sign_up.SignUpViewModel
import com.ykis.mob.ui.screens.chat.CameraScreen
import com.ykis.mob.ui.screens.chat.ChatScreen
import com.ykis.mob.ui.screens.chat.ChatViewModel
import com.ykis.mob.ui.screens.chat.ImageDetailScreen
import com.ykis.mob.ui.screens.chat.SendImageScreen
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
    chatViewModel : ChatViewModel = koinViewModel(),
    apartmentViewModel : ApartmentViewModel = koinViewModel(),
    signUpViewModel: SignUpViewModel = koinViewModel(),
    contentType: ContentType,
    displayFeatures: List<DisplayFeature>,
    navigationType: NavigationType,
) {
    val appState = rememberAppState()
    var isMainScreen by rememberSaveable {
        mutableStateOf(false)
    }
    var isRailExpanded by rememberSaveable {
        mutableStateOf(false)
    }
    val padding = when{
        (navigationType == NavigationType.NAVIGATION_RAIL_COMPACT || navigationType == NavigationType.NAVIGATION_RAIL_EXPANDED )  && !isRailExpanded && isMainScreen-> Modifier.padding(start = 80.dp)
        navigationType == NavigationType.BOTTOM_NAVIGATION && isMainScreen-> Modifier.padding(bottom = 80.dp)
        (navigationType == NavigationType.NAVIGATION_RAIL_COMPACT || navigationType == NavigationType.NAVIGATION_RAIL_EXPANDED)  && isRailExpanded && isMainScreen -> Modifier.padding(start = 260.dp)
        else -> Modifier.padding(0.dp)
    }
    val selectedUser by chatViewModel.selectedUser.collectAsStateWithLifecycle()
    val baseUIState by apartmentViewModel.uiState.collectAsStateWithLifecycle()
  val isGlobalLoading = baseUIState.isGlobalLoading || baseUIState.mainLoading


  val selectedImageUri by chatViewModel.selectedImageUri.collectAsStateWithLifecycle()
    val chatUid = remember(baseUIState, selectedUser.uid) {
        if (baseUIState.userRole == UserRole.StandardUser) {
            baseUIState.uid.toString()
        } else selectedUser.uid
    }
//    val firstScreen = remember(baseUIState.userRole) {
//        apartmentViewModel.
//    }
    Scaffold(
  containerColor = MaterialTheme.colorScheme.surfaceContainer,
  snackbarHost = {
            SnackbarHost(
                modifier = padding,
                hostState = appState.snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData
                    )
                }
            )
        },
    ){ paddingValues ->
        NavHost(
            modifier = modifier
              .fillMaxSize()
              .padding(paddingValues = paddingValues)
            ,
            navController = navController,
            startDestination = apartmentViewModel.onAppStart(),
            enterTransition = {
//                fadeIn()
                EnterTransition.None
            },
            exitTransition = {
//                fadeOut()
                ExitTransition.None
            },
            popEnterTransition = {
//                fadeIn()
                EnterTransition.None
            },
            popExitTransition = {
//                fadeOut()
                ExitTransition.None
            }
        ) {

            authNavGraph(
                navController,
                signUpViewModel = signUpViewModel
            )

            composable(
                route = Graph.APARTMENT,
                enterTransition = {
                    fadeIn()
                },
                exitTransition = {
                    fadeOut()
                },
                popEnterTransition = {
                    fadeIn()
                },
                popExitTransition = {
                    fadeOut()
                }
            ) {
                MainApartmentScreen(
                    contentType = contentType,
                    navigationType = navigationType,
                    displayFeatures = displayFeatures,
                    rootNavController = navController,
                    appState = appState,
                    onLaunch = {isMainScreen = true},
                    onDispose = {isMainScreen = false},
                    isRailExpanded = isRailExpanded,
                    onMenuClick = { isRailExpanded = !isRailExpanded },
                    navigateToWebView = {
                        uri->
                        Log.d("wv_test" , uri.toString())
                        navController.navigateToWebView(uri)
                    },
                    chatViewModel = chatViewModel,
                    apartmentViewModel = apartmentViewModel,
                    baseUIState = baseUIState
                )
            }

            composable(
                route = WebViewScreen.routeWithArgs,
                arguments = WebViewScreen.arguments
            ){
                    navBackStackEntry->
                val uri =
                    navBackStackEntry.arguments?.getString(WebViewScreen.link)
                WebView(
                    uri = uri.toString()
                )
            }
            composable(ChatScreen.route){
              ChatScreen(
                  userEntity = selectedUser,
                  navigateBack = {
                      navController.navigateUp()
                  },
                  chatViewModel = chatViewModel,
                  baseUIState = baseUIState,
                  navigateToSendImageScreen = {
                      navController.navigate(SendImageScreen.route)
                  },
                  chatUid = chatUid,
                  navigateToCameraScreen = {
                      navController.navigate(CameraScreen.route)
                  },
                  navigateToImageDetailScreen = {
                      chatViewModel.setSelectedMessage(it)
                      navController.navigate(ImageDetailScreen.route)
                  }
              )
            }

          composable(SendImageScreen.route) {
            val messageText by chatViewModel.messageText.collectAsStateWithLifecycle()
            val isLoadingAfterSending by chatViewModel.isLoadingAfterSending.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current

            LaunchedEffect(Unit) {
              if (selectedImageUri != Uri.EMPTY) {
                chatViewModel.analyzePhotoWithGemini(selectedImageUri, context)
              }
            }

            SendImageScreen(
              imageUri = selectedImageUri,
              messageText = messageText,
              onMessageTextChanged = {
                chatViewModel.onMessageTextChanged(it)
              },
              navigateBack = {
                navController.navigateUp()
              },
              onSent = {
                chatViewModel.uploadPhotoAndSendMessage(
                  context = context,
                  chatUid = chatUid,
                  senderUid = baseUIState.uid.toString(),
                  senderDisplayedName = if (baseUIState.displayName.isNullOrEmpty())
                    baseUIState.email.toString() else baseUIState.displayName.toString(),
                  senderLogoUrl = baseUIState.photoUrl,
                  role = baseUIState.userRole,
                  senderAddress = if (baseUIState.userRole == UserRole.StandardUser)
                    baseUIState.address ?: "" else "",
                  osbbId = if (baseUIState.userRole == UserRole.OsbbUser)
                    baseUIState.osbbRoleId ?: 0 else baseUIState.osmdId,
                  recipientTokens = emptyList(),
                  onComplete = {
                    navController.navigate(ChatScreen.route) {
                      popUpTo(ChatScreen.route) { inclusive = true }
                    }
                  }
                )
              },
              isLoadingAfterSending = isLoadingAfterSending,
              chatViewModel = chatViewModel
            )
          }

          composable(CameraScreen.route){
              CameraScreen(
                  navController = navController,
                  setImageUri = {
                      chatViewModel.setSelectedImageUri(it)
                  }
              )
            }
            composable(ImageDetailScreen.route){
                val selectedMessage by chatViewModel.selectedMessage.collectAsStateWithLifecycle()
              ImageDetailScreen(
                  navigateUp = {navController.navigateUp()},
                  messageEntity = selectedMessage
              )
            }
        }
//      GlobalLoadingBarrier(isVisible = isGlobalLoading)
    }
}

private fun NavHostController.navigateToWebView(uri: String) {
    this.navigate("${WebViewScreen.route}/$uri")
}






