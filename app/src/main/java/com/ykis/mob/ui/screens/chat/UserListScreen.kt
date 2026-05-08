package com.ykis.mob.ui.screens.chat

import android.R.attr.subtitle
import android.R.attr.visible
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.R
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.navigation.NavigationType
import com.ykis.mob.ui.screens.service.list.TotalDebtState
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import com.ykis.mob.ui.screens.service.list.assembleServiceList

@Composable
fun UserListScreen(
  modifier: Modifier = Modifier,
  userList: List<UserEntity>,
  baseUIState: BaseUIState,
  onUserClicked: (UserEntity) -> Unit,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType,
  chatViewModel: ChatViewModel
) {
  val methodName = "UserListScreen"
  val isForwardingMode by chatViewModel.isForwardingMode.collectAsStateWithLifecycle()
  val messageToForward by chatViewModel.forwardingMessage.collectAsStateWithLifecycle()
  val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()

  // Получаем выбранную на предыдущем экране службу
  val selectedService by chatViewModel.selectedService.collectAsStateWithLifecycle()

  LaunchedEffect(baseUIState.userRole, baseUIState.addressId) {
    Log.d("YkisLog", "$methodName: [ENTER] Role: ${baseUIState.userRole} | Service: ${selectedService?.name}")
  }

  Column(modifier = modifier.fillMaxSize()) {
    // 1. ВЕРХНЯЯ ПАНЕЛЬ
    // Вставляем расчет заголовка с логированием прямо перед вызовом DefaultAppBar

    // Внутри ChatScreenContent.kt
    val appBarTitle = remember(baseUIState.userRole, selectedService) {
      val role = baseUIState.userRole
      val serviceName = selectedService?.name ?: ""

      val result = if (role == UserRole.StandardUser) {
        // Если это жилец и он пришел из раздела ОСББ (проверяем по префиксу или по факту роли)
        // Если имя сервиса содержит "ОСББ" или мы знаем, что это не горслужба
        if (serviceName.contains("ОСББ", ignoreCase = true) || selectedService?.name == "") {
          "ОСББ чати"
        } else {
          serviceName // Для Водоканала, ТБО и т.д. останется их имя
        }
      } else {
        "список доступних чатів"
      }

      Log.d("YkisLog", "UserListScreen.AppBar: [FIXED_TITLE] Result: $result | Original: $serviceName")
      result
    }



    DefaultAppBar(
      title = appBarTitle,
      subtitle = if (baseUIState.userRole == UserRole.StandardUser) {
        Log.d("YkisLog", "UserListScreen.AppBar: [SUBTITLE] Ваші адреси (Жилец)")
        "Ваші адреси"
      } else {
        ""
      },
      onDrawerClick = onDrawerClicked,
      canNavigateBack = true,
      onBackClick = {
        Log.d("YkisLog", "UserListScreen.AppBar: [BACK_CLICK] Сброс сервиса и выход")
        chatViewModel.setSelectedService(null)
        onDrawerClicked()
      },
      navigationType = navigationType
    )



    // 2. ПОИСК (Универсальный)
    if (baseUIState.userRole != UserRole.StandardUser && !isForwardingMode) {
      OutlinedTextField(
      value = searchQuery,
      onValueChange = { chatViewModel.onSearchQueryChanged(it) },
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      placeholder = { Text("Пошук...", fontSize = 14.sp) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { chatViewModel.onSearchQueryChanged("") }) {
            Icon(Icons.Default.Close, contentDescription = null)
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(12.dp)
    )
    }

    // 3. ИНДИКАТОР ПЕРЕСЫЛКИ (без изменений)
    AnimatedVisibility(visible = isForwardingMode) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.AutoMirrored.Filled.Reply, null, modifier = Modifier.size(20.dp))
          Text(
            text = stringResource(R.string.select_recipient),
            modifier = Modifier.padding(horizontal = 12.dp).weight(1f),
            style = MaterialTheme.typography.labelLarge
          )
          TextButton(onClick = { chatViewModel.cancelForwarding() }) {
            Text(stringResource(R.string.cancel))
          }
        }
      }
    }

    // 4. КОНТЕНТ (Унифицированный)
    val finalUserList = if (baseUIState.userRole == UserRole.StandardUser) {
      Log.d("YkisLog", "UserListScreen: [MAPPING_START] Трансформация ${baseUIState.apartments.size} квартир для Жильца")

      // Для жильца превращаем его квартиры в сущности пользователей для списка
      baseUIState.apartments.map { apt ->
        val mappedUser = UserEntity(
          uid = baseUIState.uid ?: "",
          address = apt.address,
          addressId = apt.addressId,
          osbbId = apt.osmdId, // ПРОВЕРЬ: это поле должно быть 3 для твоего ОСББ
          displayName = apt.address,
          userRole = UserRole.StandardUser,
          nanim = apt.nanim ?: ""
        )

        Log.v("YkisLog", "UserListScreen: [MAP_ITEM] Кв: ${apt.address} | ID: ${apt.addressId} | OsbbID: ${apt.osmdId} | MyUID: ${baseUIState.uid?.takeLast(5)}")

        mappedUser
      }.filter {
        val matches = it.address.contains(searchQuery, ignoreCase = true)
        if (searchQuery.isNotEmpty()) {
          Log.v("YkisLog", "UserListScreen: [FILTER] '${it.address}' -> $matches")
        }
        matches
      }
    } else {
      Log.d("YkisLog", "UserListScreen: [ADMIN_MODE] Используем готовый список из ${userList.size} чел.")
      userList
    }

    Log.d("YkisLog", "UserListScreen: [MAPPING_FINISH] Итого строк в списке: ${finalUserList.size}")


    UserList(
      userList = finalUserList,
      baseUIState = baseUIState,
      onUserClick = { user ->
        if (isForwardingMode) {
          chatViewModel.confirmForward(user)
        } else {
          Log.d("YkisLog", "$methodName: [OPEN_CHAT] Addr: ${user.address}")
          onUserClicked(user)
        }
      },
      chatViewModel = chatViewModel
    )
  }
}









