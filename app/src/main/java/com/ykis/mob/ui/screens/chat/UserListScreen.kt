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
  onServiceClick: (TotalServiceDebt) -> Unit,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType,
  chatViewModel: ChatViewModel
) {
  val methodName = "UserListScreen"
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()
  val isForwardingMode by chatViewModel.isForwardingMode.collectAsStateWithLifecycle()
  val lastMessages by chatViewModel.lastMessages.collectAsStateWithLifecycle()
  val messageToForward by chatViewModel.forwardingMessage.collectAsStateWithLifecycle()

  // Подписка на состояние поиска
  val searchQuery by chatViewModel.searchQuery.collectAsStateWithLifecycle()

  LaunchedEffect(baseUIState.userRole, baseUIState.addressId) {
    Log.d("YkisLog", "$methodName: [ENTER] Role: ${baseUIState.userRole} | Address: ${baseUIState.addressId}")
  }

  Column(modifier = modifier.fillMaxSize()) {
    // 1. ВЕРХНЯЯ ПАНЕЛЬ
    DefaultAppBar(
      title = stringResource(id = R.string.chat),
      subtitle = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.address else "",
      onDrawerClick = onDrawerClicked,
      canNavigateBack = isForwardingMode,
      onBackClick = {
        Log.d("YkisLog", "$methodName: [CANCEL_FORWARD]")
        chatViewModel.cancelForwarding()
      },
      navigationType = navigationType
    )

    // 2. ПОИСК (Только для админов и не в режиме пересылки)
    if (baseUIState.userRole != UserRole.StandardUser && !isForwardingMode) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = {
          Log.d("YkisLog", "$methodName: [SEARCH_INPUT] Query: $it")
          chatViewModel.onSearchQueryChanged(it)
        },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Пошук за адресою чи о/р", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = {
              Log.d("YkisLog", "$methodName: [SEARCH_CLEAR]")
              chatViewModel.onSearchQueryChanged("")
            }) {
              Icon(Icons.Default.Close, contentDescription = null)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
      )
    }

    // 3. ИНДИКАТОР РЕЖИМА ПЕРЕСЫЛКИ
    AnimatedVisibility(
      visible = isForwardingMode,
      enter = expandVertically(),
      exit = shrinkVertically()
    ) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Column(
            modifier = Modifier.padding(start = 12.dp).weight(1f)
          ) {
            Text(
              text = stringResource(R.string.select_recipient),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = if (!messageToForward?.imageUrl.isNullOrEmpty()) "🖼 Фотографія"
              else messageToForward?.text ?: "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          TextButton(onClick = { chatViewModel.cancelForwarding() }) {
            Text(stringResource(R.string.cancel))
          }
        }
      }
    }

    // 4. КОНТЕНТ
    if (baseUIState.userRole != UserRole.StandardUser) {
      // --- РЕЖИМ АДМИНА ---
      UserList(
        userList = userList,
        baseUIState = baseUIState,
        onUserClick = { user ->
          if (isForwardingMode) {
            Log.d("YkisLog", "$methodName: [FORWARD_TO_USER] UID: ${user.uid}")
            chatViewModel.confirmForward(user)
          } else {
            Log.d("YkisLog", "$methodName: [OPEN_USER_CHAT] UID: ${user.uid}")
            onUserClicked(user)
          }
        },
        chatViewModel = chatViewModel
      )
    } else {
      // --- РЕЖИМ ЖИЛЬЦА ---
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          val residentServices = assembleServiceList(
            totalDebtState = TotalDebtState(),
            baseUIState = baseUIState
          )
          residentServices.forEach { service ->
            val chatId = when (service.contentDetail) {
              ContentDetail.OSBB -> "OSBB_${baseUIState.osmdId ?: baseUIState.osbbId}_${baseUIState.addressId}_${baseUIState.uid}"
              ContentDetail.WATER_SERVICE -> "WATER_SERVICE_9999_${baseUIState.addressId}_${baseUIState.uid}"
              ContentDetail.WARM_SERVICE -> "WARM_SERVICE_9998_${baseUIState.addressId}_${baseUIState.uid}"
              ContentDetail.GARBAGE_SERVICE -> "GARBAGE_SERVICE_9997_${baseUIState.addressId}_${baseUIState.uid}"
              else -> "${service.contentDetail.name}_${baseUIState.addressId}_${baseUIState.uid}"
            }
            val count = unreadCounts[chatId] ?: 0
            Box(modifier = Modifier.fillMaxWidth()) {
              Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                onClick = {
                  if (isForwardingMode) {
                    Log.d("YkisLog", "$methodName: [FORWARD_TO_SERVICE] ${service.contentDetail}")
                    chatViewModel.confirmForwardToService(service.contentDetail, baseUIState)
                  } else {
                    Log.d("YkisLog", "$methodName: [OPEN_SERVICE_CHAT] Path: $chatId")
                    onServiceClick(service)
                  }
                }
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(imageVector = service.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                  Text(
                    modifier = Modifier.weight(1f),
                    text = service.name,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.titleMedium
                  )
                }
              }
              if (count > 0 && !isForwardingMode) {
                Surface(
                  modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.error,
                  tonalElevation = 4.dp
                ) {
                  Text(
                    text = if (count > 9) "9+" else count.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}








