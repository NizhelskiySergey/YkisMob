package com.ykis.mob.ui.screens.auth.verify_email

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
  navigateBack: () -> Unit,
  isLoading: Boolean
) {
  Scaffold(
    topBar = {
      DefaultAppBar(
        title = stringResource(R.string.verify_email_title),
        canNavigateBack = true,
        onBackClick = {
          Log.d("YkisLog", "VerifyEmailUI: [BACK_CLICK] Возврат на регистрацию")
          navigateBack()
        }
      )
    },
    bottomBar = {
      Button(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(16.dp),
        onClick = {
          Log.d("YkisLog", "VerifyEmailUI: [CHECK_CLICK] Проверка статуса для $email")
          onReloadClick()
        },
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp)
      ) {
        AnimatedContent(targetState = isLoading, label = "check_loading") { loading ->
          if (loading) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp
            )
          } else {
            Text(text = "Я підтвердив пошту", style = MaterialTheme.typography.titleMedium)
          }
        }
      }
    }
  ) { padding ->
    Column(
      modifier = modifier
        .padding(padding)
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(32.dp))

      Icon(
        imageVector = Icons.Default.MarkEmailRead,
        contentDescription = null,
        modifier = Modifier.size(100.dp),
        tint = MaterialTheme.colorScheme.primary
      )

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = "Лист надіслано на:",
        style = MaterialTheme.typography.bodyLarge
      )

      Text(
        text = email,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = stringResource(id = R.string.verify_email),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(32.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = !isLoading) {
          Log.d("YkisLog", "VerifyEmailUI: [RESEND_CLICK]")
          onRepeatEmailClick()
        }
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text = "Надіслати лист ще раз",
          style = MaterialTheme.typography.labelLarge,
          textDecoration = TextDecoration.Underline,
          color = MaterialTheme.colorScheme.secondary
        )
      }
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

  LaunchedEffect(Unit) {
    Log.d("YkisLog", "VerifyEmailUI: [ENTER_SCREEN] Ожидание подтверждения для ${viewModel.email}")
  }

  VerifyEmailScreenStateless(
    modifier = Modifier,
    onRepeatEmailClick = { viewModel.repeatEmailVerified() },
    onReloadClick = {
      viewModel.reloadUser {
        // Вход в onSuccess вызывается ТОЛЬКО если почта реально подтверждена
        Log.d("YkisLog", "VerifyEmailUI: [SUCCESS] Почта подтверждена. Перезапуск...")
        restartApp(Graph.APARTMENT)
      }
    },
    email = viewModel.displayEmail,
    navigateBack = {
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
