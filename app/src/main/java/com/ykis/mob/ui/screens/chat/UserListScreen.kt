package com.ykis.mob.ui.screens.chat

import android.util.Log
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

  Column(modifier = modifier.fillMaxSize()) {
    DefaultAppBar(
      title = stringResource(id = R.string.chat),
      onDrawerClick = onDrawerClicked,
      canNavigateBack = false,
      navigationType = navigationType
    )

    if (baseUIState.userRole != UserRole.StandardUser) {
      UserList(
        userList = userList,
        baseUIState = baseUIState,
        onUserClick = onUserClicked,
        chatViewModel = chatViewModel
      )
    } else {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
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
                  // 1. Сбрасываем счетчик в базе и локально
                  chatViewModel.markMessagesAsRead(chatId)

                  // 2. Выполняем стандартный переход
                  onServiceClick(service)
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

              if (count > 0) {
                Surface(
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.error,
                  tonalElevation = 4.dp
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




