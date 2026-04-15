package com.ykis.mob.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.twotone.Email
import androidx.compose.material.icons.twotone.Key
import androidx.compose.material.icons.twotone.Nat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.ui.components.BaseCard
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.Graph
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.navigation.SignUpScreen
import com.ykis.mob.ui.theme.YkisPAMTheme
import org.koin.compose.viewmodel.koinViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  viewModel: ProfileViewModel ,
  navigationType: NavigationType,
  onDrawerClicked: () -> Unit,
  navigateToSettings: () -> Unit,
  restartApp: (String) -> Unit
) {
  val deleteResponse by viewModel.deleteAccountResponse.collectAsStateWithLifecycle()

  ProfileScreenStateless(
    photoUrl = viewModel.photoUrl,
    displayName = viewModel.displayName,
    email = viewModel.email,
    uid = viewModel.uid,
    providerId = viewModel.providerId,
    navigationType = navigationType,
    onDrawerClicked = onDrawerClicked,
    navigateToSettings = navigateToSettings,
    isLoading = deleteResponse is Resource.Loading,

    // ПЕРЕДАЕМ ВЫХОД
    onSignOut = {
      viewModel.signOut {
        restartApp(SignUpScreen.route)
      }
    },

    // ПЕРЕДАЕМ УДАЛЕНИЕ
    onDeleteAccount = {
      viewModel.deleteAccount {
        restartApp(SignUpScreen.route)
      }
    }
  )
}


@ExperimentalMaterial3Api
@Composable
fun ProfileScreenStateless(
  photoUrl: String,
  displayName: String,
  email: String,
  uid: String,
  providerId: String,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType,
  navigateToSettings: () -> Unit,
  onSignOut: () -> Unit,           // Добавлено: Выход
  onDeleteAccount: () -> Unit,     // Добавлено: Удаление
  isLoading: Boolean = false       // Статус загрузки
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    DefaultAppBar(
      title = stringResource(id = R.string.profile),
      navigationType = navigationType,
      onDrawerClick = onDrawerClicked,
      canNavigateBack = false,
      actionButton = {
        if (navigationType == NavigationType.BOTTOM_NAVIGATION) {
          IconButton(onClick = navigateToSettings) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = stringResource(id = R.string.settings),
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    )

    // --- КАРТОЧКА ПРОФИЛЯ ---
    BaseCard {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Секция Аватара и Имени
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
              .data(photoUrl)
              .crossfade(true)
              .build(),
            contentDescription = null,
            error = painterResource(id = R.drawable.ic_valve_filled),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(60.dp)
              .clip(CircleShape)
          )
          Text(
            text = displayName.ifBlank { "Пользователь" },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp)
          )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Поля данных (Email, UID, Provider)
        ProfileInfoRow(Icons.TwoTone.Email, R.string.email_colon, email)
        ProfileInfoRow(Icons.TwoTone.Key, R.string.uid_provider, uid)
        ProfileInfoRow(Icons.TwoTone.Nat, R.string.provider, providerId)
      }
    }

    Spacer(modifier = Modifier.height(32.dp))

    // --- КНОПКИ УПРАВЛЕНИЯ ---
    if (isLoading) {
      CircularProgressIndicator()
    } else {
      // Выход из системы
      TextButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
      ) {
        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Выйти из аккаунта")
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Удаление аккаунта (Красная кнопка)
      OutlinedButton(
        onClick = onDeleteAccount,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
      ) {
        Icon(Icons.Default.DeleteForever, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Удалить аккаунт навсегда")
      }

      Text(
        text = "Внимание: удаление нельзя отменить",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    Spacer(modifier = Modifier.height(32.dp))
  }
}

@Composable
fun ProfileInfoRow(icon: ImageVector, labelRes: Int, value: String) {
  Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.primary
      )
      Text(
        text = stringResource(id = labelRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp)
      )
    }
    Text(
      text = value,
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(start = 26.dp, top = 2.dp)
    )
  }
}





