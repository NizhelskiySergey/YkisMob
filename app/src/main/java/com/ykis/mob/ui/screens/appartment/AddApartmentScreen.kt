package com.ykis.mob.ui.screens.appartment

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ykis.mob.R
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.core.snackbar.SnackbarMessage.Companion.toMessage
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.AddApartmentScreen
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.navigation.InfoApartmentScreenDest
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.navigation.navigateToInfoApartment
import com.ykis.mob.ui.theme.YkisPAMTheme
import kotlinx.coroutines.coroutineScope
import org.koin.compose.viewmodel.koinViewModel
@Composable
fun AddApartmentScreenContent(
  modifier: Modifier = Modifier,
  viewModel: ApartmentViewModel = koinViewModel(),
  navController: NavHostController,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType,
  closeContentDetail: () -> Unit
) {
  val secretCode by viewModel.secretCode.collectAsStateWithLifecycle("")
  val snackbarHostState = remember { SnackbarHostState() }
  val snackbarMessage by SnackbarManager.snackbarMessages.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val keyboard = LocalSoftwareKeyboardController.current

  // СЛУШАТЕЛЬ ОШИБОК (Снэкбары)
  LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let {
      val text = it.toMessage(context.resources)
      Log.d("YkisLog", "AddApartmentScreen: [DISPLAYING] $text")

      // Показываем сообщение
      snackbarHostState.showSnackbar(
        message = text,
        duration = SnackbarDuration.Short
      )

      // ТОЛЬКО ТЕПЕРЬ очищаем стейт менеджера
      SnackbarManager.clearMessage()
    }
  }


  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) } // Теперь ошибки будет видно!
  ) { padding ->
    AddApartmentScreenStateless(
      modifier = Modifier.padding(padding),
      isButtonEnabled = secretCode.trim().isNotEmpty(),
      onDrawerClicked = onDrawerClicked,
      onAddClick = {
        Log.d("YkisLog", "AddApartmentScreen: [CLICK] Код: $secretCode")
        keyboard?.hide()

        viewModel.addApartment {
          Log.d("YkisLog", "AddApartmentScreen: [SUCCESS] Переход на Info")
          closeContentDetail()
          navController.navigate(InfoApartmentScreenDest.route) {
            popUpTo(AddApartmentScreen.route) { inclusive = true }
            launchSingleTop = true
          }
        }
      },
      navigationType = navigationType,
      code = secretCode,
      onCodeChanged = { newValue ->
        Log.d("YkisLog", "AddApartmentScreen: [INPUT] $newValue")
        viewModel.onSecretCodeChange(newValue)
      }
    )
  }
}

@Composable
fun AddApartmentScreenStateless(
  modifier: Modifier = Modifier,
  isButtonEnabled: Boolean,
  onDrawerClicked: () -> Unit,
  onAddClick: () -> Unit,
  navigationType: NavigationType,
  code: String,
  onCodeChanged: (String) -> Unit
) {
  Column(modifier = modifier.fillMaxSize()) {
    DefaultAppBar(
      navigationType = navigationType,
      onDrawerClick = onDrawerClicked,
      title = stringResource(id = R.string.add_appartment),
      canNavigateBack = false
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Card(
        modifier = Modifier.widthIn(max = 500.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Text(
            text = stringResource(id = R.string.tooltip_code),
            style = MaterialTheme.typography.bodyMedium
          )

          Spacer(modifier = Modifier.height(16.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            OutlinedTextField(
              value = code,
              onValueChange = onCodeChanged,
              modifier = Modifier.weight(1f),
              label = { Text(stringResource(id = R.string.secret_сode)) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
              singleLine = true
            )

            Button(
              onClick = onAddClick,
              enabled = isButtonEnabled,
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(text = stringResource(id = R.string.add))
            }
          }
        }
      }
    }
  }
}



@Preview
@Composable
private fun AddApartmentPreview() {
  YkisPAMTheme {
    Box(modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)) {
      AddApartmentScreenStateless(
        isButtonEnabled = true,
        onDrawerClicked = {},
        onAddClick = {},
        navigationType = NavigationType.BOTTOM_NAVIGATION,
        code = "",
        onCodeChanged = {}
      )
    }
  }
}
