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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()
  val isForwardingMode by chatViewModel.isForwardingMode.collectAsStateWithLifecycle()
  val messageToForward by chatViewModel.forwardingMessage.collectAsStateWithLifecycle()

  Column(modifier = modifier.fillMaxSize()) {
    // 1. ВЕРХНЯЯ ПАНЕЛЬ
    DefaultAppBar(
      title =  stringResource(id = R.string.chat),
      subtitle =  if (baseUIState.userRole== UserRole.StandardUser) baseUIState.address else "", // Добавили адрес текущей квартиры
      onDrawerClick = onDrawerClicked,
      canNavigateBack = isForwardingMode, // В режиме пересылки можно нажать "назад"
      onBackClick = { chatViewModel.cancelForwarding() },
      navigationType = navigationType
    )



    // 2. ИНДИКАТОР РЕЖИМА ПЕРЕСЫЛКИ
    // 1. Подписываемся на само сообщение (это уберет Warning "never used")
    val messageToForward by chatViewModel.forwardingMessage.collectAsStateWithLifecycle()

    AnimatedVisibility(
      visible = isForwardingMode,
      enter = expandVertically(),
      exit = shrinkVertically()
    ) {
      Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp // Добавим немного глубины
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Иконка пересылки
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
          )

          Column(
            modifier = Modifier
              .padding(start = 12.dp)
              .weight(1f)
          ) {
            Text(
              text = stringResource(R.string.select_recipient),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary
            )
            // ПРЕВЬЮ: показываем текст или пометку "Фото"
            Text(
              text = if (!messageToForward?.imageUrl.isNullOrEmpty())
                "🖼 Фотография"
              else
                messageToForward?.text ?: "",
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


    if (baseUIState.userRole != UserRole.StandardUser) {
      // --- РЕЖИМ АДМИНА ---
      UserList(
        userList = userList,
        baseUIState = baseUIState,
        onUserClick = { user ->
          if (isForwardingMode) {
            chatViewModel.confirmForward(user)
          } else {
            onUserClicked(user)
          }
        },
        chatViewModel = chatViewModel
      )
    } else {
      // --- РЕЖИМ ЖИЛЬЦА ---
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
          modifier = Modifier
            .width(IntrinsicSize.Max)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          assembleServiceList(
            totalDebtState = TotalDebtState(),
            baseUIState = baseUIState
          ).forEach { service ->
            val servicePrefix = service.contentDetail.name
            val chatId = if (service.contentDetail == ContentDetail.OSBB) {
              "OSBB_${baseUIState.osbbId}_${baseUIState.addressId}_${baseUIState.uid}"
            } else {
              "${servicePrefix}_${baseUIState.addressId}_${baseUIState.uid}"
            }

            val count = unreadCounts[chatId] ?: 0

            Box(modifier = Modifier.fillMaxWidth()) {
              Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                  if (isForwardingMode) {
                    chatViewModel.confirmForwardToService(service.contentDetail, baseUIState)
                  } else {
                    onServiceClick(service)
                  }
                },
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(imageVector = service.icon, contentDescription = null)
                  Text(
                    modifier = Modifier.weight(1f),
                    text = service.name,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.titleMedium
                  )
                }
              }

              // BADGE
              if (count > 0 && !isForwardingMode) {
                Surface(
                  modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.error
                ) {
                  Text(
                    text = if (count > 9) "9+" else count.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
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






