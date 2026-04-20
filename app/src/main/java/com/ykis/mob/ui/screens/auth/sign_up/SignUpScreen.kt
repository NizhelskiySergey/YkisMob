package com.ykis.mob.ui.screens.auth.sign_up

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.composable.EmailField
import com.ykis.mob.core.composable.LogoImage
import com.ykis.mob.core.composable.PasswordField
import com.ykis.mob.core.composable.RepeatPasswordField
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.VerifyEmailScreen
import com.ykis.mob.ui.screens.auth.sign_up.components.SignUpUiState
import com.ykis.mob.ui.theme.YkisPAMTheme

@Composable
fun SignUpScreenStateless(
  modifier: Modifier = Modifier,
  signUpUiState: SignUpUiState,
  navigateBack : () -> Unit,
  onEmailChange: (String) -> Unit,
  onPasswordChange: (String) -> Unit,
  onRepeatPasswordChange: (String) -> Unit,
  onSignUpClick: () -> Unit,
  isLoading: Boolean
) {
  var checkedState by rememberSaveable { mutableStateOf(false) }
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .widthIn(max = 460.dp),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      DefaultAppBar(
        title = "Реєстрація",
        canNavigateBack = true,
        onBackClick = {
          Log.d("YkisLog", "SignUpUI: [BACK_CLICK]")
          navigateBack()
        }
      )
      Column(
        modifier = Modifier
          .padding(horizontal = 16.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(16.dp))
        LogoImage()
        Spacer(modifier = Modifier.height(16.dp))

        EmailField(signUpUiState.email, onEmailChange, modifier)
        Spacer(modifier = Modifier.height(8.dp))

        PasswordField(signUpUiState.password, onPasswordChange)
        Spacer(modifier = Modifier.height(8.dp))

        RepeatPasswordField(signUpUiState.repeatPassword, onRepeatPasswordChange, modifier)
        Spacer(modifier = Modifier.height(16.dp))

        Button(
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            Log.d("YkisLog", "SignUpUI: [SUBMIT_CLICK] Email: ${signUpUiState.email}")
            onSignUpClick()
          },
          enabled = true
        ) {
          AnimatedContent(targetState = isLoading, label = "loading_anim") { loading ->
            if (loading) {
              CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
              )
            } else {
              Text(
                text = stringResource(R.string.sign_up),
                style = MaterialTheme.typography.titleMedium
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
fun SignUpScreen(
  modifier: Modifier = Modifier,
  viewModel: SignUpViewModel,
  navController: NavController
) {
  val keyboard = LocalSoftwareKeyboardController.current
  val signUpUiState by viewModel.signUpUiState
  val signUpResponse by viewModel.signUpResponse.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    Log.d("YkisLog", "SignUpUI: [ENTER_SCREEN]")
  }

  LaunchedEffect(signUpResponse) {
    if (signUpResponse is Resource.Success) {
      Log.d("YkisLog", "SignUpUI: [NAVIGATE] Success. To VerifyEmail")
      navController.navigate(VerifyEmailScreen.route) {
        popUpTo(com.ykis.mob.ui.navigation.SignUpScreen.route) { inclusive = true }
      }
    }
  }

  SignUpScreenStateless(
    signUpUiState = signUpUiState,
    navigateBack = { navController.navigateUp() },
    onEmailChange = viewModel::onEmailChange,
    onPasswordChange = viewModel::onPasswordChange,
    onRepeatPasswordChange = viewModel::onRepeatPasswordChange,
    onSignUpClick = {
      keyboard?.hide()
      viewModel.signUpWithEmailAndPassword { }
    },
    isLoading = signUpResponse is Resource.Loading
  )
}


@Preview
@Composable
private fun SignUpScreenPreview() {
    YkisPAMTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            SignUpScreenStateless(
                signUpUiState = SignUpUiState(),
                navigateBack = { },
                onEmailChange = { },
                onPasswordChange = { },
                onRepeatPasswordChange = { },
                onSignUpClick = {},
                isLoading = true
            )
        }
    }
}
