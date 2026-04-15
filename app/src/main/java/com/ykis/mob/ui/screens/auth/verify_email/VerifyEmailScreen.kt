package com.ykis.mob.ui.screens.auth.verify_email

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.screens.auth.sign_up.SignUpViewModel
import com.ykis.mob.ui.theme.YkisPAMTheme
import com.ykis.mob.R.string as AppText

@Composable
fun VerifyEmailScreenStateless(
    modifier: Modifier = Modifier,
    onReloadClick: () -> Unit,
    onRepeatEmailClick: () -> Unit,
    email: String,
    navigateBack :()->Unit,
    isLoading:Boolean
) {
  Scaffold(
    topBar = {
      DefaultAppBar(
        title = stringResource(R.string.verify_email_title),
        canNavigateBack = true,
        onBackClick = navigateBack
      )
    },
    bottomBar = {
      Button(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        onClick = onReloadClick,
        enabled = !isLoading
      ) {
        // Твой AnimatedContent с лоадером
      }
    }
  ) { padding ->
    Column(
      modifier = modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(32.dp))
      // Твой Image, Text и Row с повторной отправкой
    }
  }
}

@Composable
fun VerifyEmailScreen(
  viewModel: SignUpViewModel,
  restartApp: (String) -> Unit,
  navController: NavController
) {
  val reloadUserResponse by viewModel.reloadUserResponse.collectAsStateWithLifecycle()

  VerifyEmailScreenStateless(
    onRepeatEmailClick = { viewModel.repeatEmailVerified() },
    onReloadClick = {
      viewModel.reloadUser {
        if (viewModel.isEmailVerified) {
          // Переходим в основную часть приложения
          restartApp(Graph.APARTMENT)
        } else {
          // Если нажал "Далее", но письмо в почте не кликнул
          SnackbarManager.showMessage(AppText.email_not_verified_message)
        }
      }
    },
    email = viewModel.email,
    navigateBack = {
      // Если пользователь хочет вернуться и сменить почту,
      // нам нужно разрешить ему вернуться на SignUp
      navController.popBackStack()
    },
    isLoading = reloadUserResponse is Resource.Loading
  )
}


@Preview
@Composable
private fun VerifyEmailScreenPreview() {
    YkisPAMTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            VerifyEmailScreenStateless(
               onReloadClick = {},
                onRepeatEmailClick = {},
                email = "rshulik74@gmail.com",
                navigateBack = {},
                isLoading = false
            )
        }
    }
}
