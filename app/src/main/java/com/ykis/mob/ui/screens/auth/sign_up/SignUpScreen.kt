package com.ykis.mob.ui.screens.auth.sign_up

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.ykis.mob.ui.screens.auth.sign_up.components.AgreementCheckBox
import com.ykis.mob.ui.screens.auth.sign_up.components.SignUpUiState
import com.ykis.mob.ui.theme.YkisPAMTheme
import com.ykis.mob.R.string as AppText


@Composable
fun SignUpScreenStateless(
    modifier: Modifier = Modifier,
    signUpUiState: SignUpUiState,
    navigateBack : () -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
    onSignUpClick: () -> Unit,
    isLoading:Boolean
) {
    var isExpanded by remember{
        mutableStateOf(true)
    }
    var checkedState by rememberSaveable { mutableStateOf(false) }
    val termHeight by animateDpAsState(
        targetValue = if(isExpanded) 824.dp else 64.dp, label = "",
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        )
    )
    val iconAngle by animateFloatAsState(
        targetValue = if(isExpanded) 0f else 180f, label = "",
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioLowBouncy
        )
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ){
        Column(
            modifier = modifier
                .fillMaxHeight()
                .widthIn(max = 460.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DefaultAppBar(
                title = "Реєстрація",
                canNavigateBack = true,
                onBackClick = {
                    navigateBack()
                }
            )
            Column(
                modifier = modifier
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
                RepeatPasswordField(
                    signUpUiState.repeatPassword,
                    onRepeatPasswordChange,
                    modifier
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.height(termHeight).clickable {
                        isExpanded = !isExpanded
                    },
                )
                {
                    Column(
                        modifier = modifier.padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = modifier.weight(1f),
                                text = stringResource(R.string.agreement_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Icon(
                                modifier = modifier.rotate(iconAngle),
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = null
                            )
                        }
                        Text(
                            text =
                            stringResource(id = AppText.agreement_text),
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = TextAlign.Start,
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AgreementCheckBox(
                        checked = checkedState,
                        onCheckedChange = {
                            checkedState = it
                            isExpanded = false
                        },

                        modifier = Modifier
                    )
                }
                Button(
                    modifier = modifier.fillMaxWidth(),
                    onClick = {
                        onSignUpClick()
                    },
                    enabled = checkedState
                ) {
                    AnimatedContent(isLoading) {
                        if(it) {
                            CircularProgressIndicator(
                                modifier = modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }else{
                            Text(
                                text = stringResource(R.string.sign_up),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                }
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

  // Слушаем ответ от сервера
  LaunchedEffect(signUpResponse) {
    if (signUpResponse is Resource.Success) {
      // Переходим только если регистрация реально прошла успешно
      navController.navigate(VerifyEmailScreen.route) {
        popUpTo(com.ykis.mob.ui.navigation.SignUpScreen.route) {
          inclusive = true
        }
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
      // Вызываем один метод, который отвечает за ВСЮ процедуру регистрации
      viewModel.signUpWithEmailAndPassword {
        // Здесь можно ничего не делать, так как LaunchedEffect выше обработает переход
      }
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
