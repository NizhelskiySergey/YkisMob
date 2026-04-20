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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.ykis.mob.R
import com.ykis.mob.core.Resource
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
fun AuthenticationButton(
  buttonText: Int,
  isLoading: Boolean,
  onRequestResult: (Credential) -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  // Локальный стейт для выбора аккаунта
  var isChoosingAccount by remember { mutableStateOf(false) }

  OutlinedButton(
    onClick = {
      coroutineScope.launch {
        Log.d("YkisLog", "AuthButton: [CLICK] Включаем локальный лоадер")
        isChoosingAccount = true // 1. Включаем лоадер СРАЗУ

        launchCredManButtonUI(
          context = context,
          onFinished = {
            // 3. Системное окно закрылось.
            // Выключаем локальный лоадер. К этому моменту
            // глобальный isLoading уже должен быть true (если аккаунт выбран)
            isChoosingAccount = false
            Log.d("YkisLog", "AuthButton: [FINISHED] Локальный лоадер выключен")
          },
          onRequestResult = { credential ->
            // 2. Передаем данные. Внутри ViewModel ПЕРВОЙ строкой
            // должно идти: signInWithGoogleResponse = Resource.Loading()
            Log.d("YkisLog", "AuthButton: [DATA] Передача во ViewModel")
            onRequestResult(credential)
          }
        )
      }
    },
    modifier = Modifier.fillMaxWidth(),
    // Блокируем кнопку, пока идет любой из процессов
    enabled = !isLoading && !isChoosingAccount,
    shape = RoundedCornerShape(12.dp)
  ) {
    // Если активен хотя бы один лоадер — показываем CircularProgressIndicator
    if (isLoading || isChoosingAccount) {
      CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.primary
      )
    } else {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_google_logo),
          contentDescription = "Google logo",
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = stringResource(buttonText),
          style = MaterialTheme.typography.titleMedium
        )
      }
    }
  }
}





private suspend fun launchCredManButtonUI(
  context: Context,
  onFinished: () -> Unit, // Сигнал для выключения лоадера
  onRequestResult: (Credential) -> Unit
) {
  val methodName = "Auth.launchCredMan"
  try {
    Log.d("YkisLog", "$methodName: [START] Открытие системного окна Google")

    val googleIdOption = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(false) // Позволяет выбрать любой аккаунт на устройстве
      .setServerClientId("1062920014188-8s41hcrkkik155m7mo2spj26jupp27e5.apps.googleusercontent.com")
      .setAutoSelectEnabled(false)
      .build()

    val request = GetCredentialRequest.Builder()
      .addCredentialOption(googleIdOption)
      .build()

    val result = CredentialManager.create(context).getCredential(
      request = request,
      context = context
    )

    Log.d("YkisLog", "$methodName: [SUCCESS] Аккаунт выбран")
    onRequestResult(result.credential)

  } catch (e: GetCredentialException) {
    when (e) {
      is GetCredentialCancellationException -> {
        Log.d("YkisLog", "$methodName: [CANCEL] Пользователь закрыл окно")
      }
      else -> {
        Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
        SnackbarManager.showMessage("Помилка авторизації: ${e.localizedMessage}")
      }
    }
  } finally {
    // КРИТИЧЕСКИ ВАЖНО: всегда уведомляем кнопку об окончании,
    // чтобы выключить локальный лоадер (isChoosingAccount = false)
    onFinished()
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

                  val isGoogleLoading = false
                  AuthenticationButton(
                    buttonText = R.string.sign_in_with_google,
                    isLoading = isGoogleLoading, // Передаем флаг
                    onRequestResult = { credential ->
                      onGoogleClick(credential)
                    }
                  )
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
  val googleResponse = viewModel.signInWithGoogleResponse
  val isGoogleLoading = googleResponse is Resource.Loading<*>
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
      onGoogleClick = { credential ->
        // Сначала чистим вьюмодель

        viewModel.onSignUpWithGoogle(credential, openAndPopUp = {
          navController.navigate(Graph.APARTMENT) {
            // 1. Очищаем весь стек до основания
            popUpTo(0) { inclusive = true }
            // 2. ЗАПРЕЩАЕМ восстанавливать старый стейт (это убьет "призраков")
            restoreState = false
            launchSingleTop = true
          }
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
