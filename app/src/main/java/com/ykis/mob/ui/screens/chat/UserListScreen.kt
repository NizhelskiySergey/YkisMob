package com.ykis.mob.ui.screens.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ykis.mob.R
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
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
  onServiceClick : (TotalServiceDebt) -> Unit,
  onDrawerClicked: () -> Unit,
  navigationType: NavigationType,
  chatViewModel: ChatViewModel
) {
  Column(
    modifier = modifier.fillMaxSize()
  ) {
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
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(
          modifier = modifier.width(IntrinsicSize.Max)
        ) {
          assembleServiceList(
            totalDebtState = TotalDebtState(),
            baseUIState = baseUIState
          ).forEach {
            Button(
              modifier = modifier.fillMaxWidth(),
              onClick = {
                onServiceClick(it)
              }
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(imageVector = it.icon, contentDescription = null)
                Text(
                  modifier = modifier.weight(1f),
                  text = it.name,
                  textAlign = TextAlign.Center
                )
              }
            }
          }
        }
      }
    }

  }
}
