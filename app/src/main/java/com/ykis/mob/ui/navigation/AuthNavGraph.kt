package com.ykis.mob.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.ykis.mob.ui.screens.auth.sign_in.SignInScreen
import com.ykis.mob.ui.screens.auth.sign_up.SignUpScreen
import com.ykis.mob.ui.screens.auth.sign_up.SignUpViewModel
import com.ykis.mob.ui.screens.auth.verify_email.VerifyEmailScreen
import org.koin.compose.viewmodel.koinViewModel

fun NavGraphBuilder.authNavGraph(
  navController: NavHostController,signUpViewModel: SignUpViewModel
) {
  navigation(
    route = Graph.AUTHENTICATION,
    startDestination = SignInScreen.route
  ) {
    composable(SignInScreen.route) {
      SignInScreen(
        openScreen = { route -> navController.navigate(route) },
        navController = navController
      )
    }

    composable(SignUpScreen.route) {
      // ViewModel создается только для этого экрана
      val viewModel: SignUpViewModel = koinViewModel()
      SignUpScreen(
        viewModel = viewModel,
        navController = navController
      )
    }

    composable(VerifyEmailScreen.route) {
      // Если VerifyEmailScreen должен делить данные с SignUp,
      // используйте koinViewModel(viewModelStoreOwner = NavBackStackEntry)
      val viewModel: SignUpViewModel = koinViewModel()
      VerifyEmailScreen(
        restartApp = { route -> navController.navigate(route) {
          popUpTo(Graph.AUTHENTICATION) { inclusive = true }
        }},
        viewModel = viewModel,
        navController = navController
      )
    }
  }
}
