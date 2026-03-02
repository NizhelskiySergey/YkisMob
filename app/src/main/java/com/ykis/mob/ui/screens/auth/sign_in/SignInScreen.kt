package com.ykis.mob.ui.screens.auth.sign_in

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.ykis.mob.R
import com.ykis.mob.core.composable.EmailField
import com.ykis.mob.core.composable.LogoImage
import com.ykis.mob.core.composable.PasswordField
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.theme.YkisPAMTheme
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthenticationButton(buttonText: Int, onRequestResult: (Credential) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    OutlinedButton(
        onClick = { coroutineScope.launch {
            launchCredManButtonUI(context, onRequestResult) }

        },
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ){
            Image(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = "Google logo"
            )

            Text(
                text = stringResource(buttonText),
                style = MaterialTheme.typography.titleMedium
            )
        }

    }
}

private suspend fun launchCredManButtonUI(
  context: Context,
  onRequestResult: (Credential) -> Unit
) {
  try {
    // Более стабильный вариант для работы с Firebase
    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(false) // Показывать все аккаунты
      .setServerClientId("1062920014188-8s41hcrkkik155m7mo2spj26jupp27e5.apps.googleusercontent.com")
      .setAutoSelectEnabled(false) // Лучше false для первого входа
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    val result = CredentialManager.create(context).getCredential(
      request = request,
      context = context
    )

    onRequestResult(result.credential)
  } catch (e: GetCredentialException) {
    // Обработка отмены пользователем (чтобы не спамить в лог)
    if (e is GetCredentialCancellationException) {
      Log.d("Auth", "User cancelled")
    } else {
      Log.e("Auth", "Error: ${e.message}")
      SnackbarManager.showMessage("Помилка авторизації")
    }
  }
}

@Composable
fun SignInScreenStateless(
    modifier: Modifier = Modifier,
    email : String,
    onEmailChange : (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSignInClick : ()->Unit,
    onForgotPasswordClick : () ->Unit,
    onSignUpClick : () -> Unit,
    onGoogleClick : (Credential) -> Unit
) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            Column(
                modifier = modifier
                    .fillMaxHeight()
                    .widthIn(max = 460.dp) ,
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DefaultAppBar(
                    title = stringResource(R.string.login_details),
                    canNavigateBack = false,
                )
                Column(
                    modifier = modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                    ,
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LogoImage()
                    Spacer(modifier = Modifier.height(16.dp))
                    EmailField(email, onEmailChange, modifier)
                    Spacer(modifier = Modifier.height(8.dp))
                    PasswordField(password, onPasswordChange)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            modifier = modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    onForgotPasswordClick()
                                }
                                .padding(4.dp),
                            text = stringResource(R.string.forget_password)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        modifier = modifier.fillMaxWidth(),
                        onClick = {
                            onSignInClick()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.sign_in),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Row(
                        modifier = modifier.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline))
                        Text(
                            modifier = modifier.padding(horizontal = 4.dp),
                            text = stringResource(R.string.or),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Box(modifier = modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline))
                    }
                    AuthenticationButton(buttonText = R.string.sign_in_with_google) { credential ->
                        onGoogleClick(credential)
                    }
                    Spacer(modifier = modifier.height(8.dp))
                    Row(
                        modifier = modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.have_no_account)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            modifier = modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    onSignUpClick()
                                },
                            color = MaterialTheme.colorScheme.primary,
                            text = stringResource(R.string.sign_up)
                        )
                    }
                }
            }
        }

}

@Composable
fun SignInScreen(
    openScreen: (String) -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
    navController: NavController
) {
    val singInUiState = viewModel.singInUiState
    val keyboard = LocalSoftwareKeyboardController.current

    SignInScreenStateless(
        email = singInUiState.email,
        onEmailChange = viewModel::onEmailChange,
        password = singInUiState.password,
        onPasswordChange = viewModel::onPasswordChange,
        onSignInClick = {
            keyboard?.hide()
            viewModel.onSignInClick(openScreen)
        },
        onForgotPasswordClick = {
            viewModel.onForgotPasswordClick()
        },
        onSignUpClick = {
            viewModel.onSignUpClick(openScreen)
        },
        onGoogleClick = {
            viewModel.onSignUpWithGoogle(it , openAndPopUp = {
                navController.navigate(Graph.APARTMENT)
            })
        }
    )
}

@Preview(showBackground = true, device = "spec:parent=pixel_5,orientation=landscape")
@Composable
private fun SignInScreenPreview() {
    YkisPAMTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ){
            SignInScreenStateless(
                email = "",
                onEmailChange = {},
                password = "",
                onSignInClick = {},
                onForgotPasswordClick = {},
                onSignUpClick = {},
                onPasswordChange = {},
                onGoogleClick = {},
            )
        }
    }
}
