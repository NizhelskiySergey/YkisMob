package com.ykis.mob.ui.screens.chat

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState


data class UserWithLatestMessage(
  val user: UserEntity,
  val latestMessage: MessageEntity
)
@Composable
fun UserList(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  userList: List<UserEntity>,
  onUserClick: (UserEntity) -> Unit,
  chatViewModel: ChatViewModel
) {
  val latestMessages by chatViewModel.lastMessages.collectAsStateWithLifecycle()
  // 1. Подписываемся на счетчик непрочитанных сообщений
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()

  val userWithMessages = remember(userList, latestMessages) {
    userList.map { user ->
      val chatId = if (baseUIState.userRole == UserRole.OsbbUser) {
        "OSBB_${baseUIState.osbbId}_${user.addressId}_${user.uid}"
      } else {
        "${baseUIState.userRole.codeName.name}_${user.addressId}_${user.uid}"
      }

      val lastMsg = latestMessages[chatId] ?: MessageEntity(text = "Нет сообщений")

      val stableDisplayName = lastMsg.senderAddress.ifBlank {
        user.displayName ?: ""
      }

      UserWithLatestMessage(
        user = user.copy(displayName = stableDisplayName),
        latestMessage = lastMsg
      )
    }.sortedByDescending { it.latestMessage.timestamp }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 16.dp)
  ) {
    items(
      items = userWithMessages,
      key = { "${it.user.uid}_${it.user.addressId}" }
    ) { item ->
      // 2. Вычисляем ключ чата для поиска кол-ва сообщений
      val chatId = if (baseUIState.userRole == UserRole.OsbbUser) {
        "OSBB_${baseUIState.osbbId}_${item.user.addressId}_${item.user.uid}"
      } else {
        "${baseUIState.userRole.codeName.name}_${item.user.addressId}_${item.user.uid}"
      }
      val count = unreadCounts[chatId] ?: 0

      Box(modifier = Modifier.fillMaxWidth()) {
        UserListItem(
          it = item.user,
          onUserClick = onUserClick,
          lastMessage = item.latestMessage,
          currentUid = baseUIState.uid.toString()
        )

        // 3. Отрисовка красного индикатора (Badge)
        if (count > 0) {
          Surface(
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .padding(end = 24.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error, // Красный цвет
            tonalElevation = 4.dp
          ) {
            Text(
              text = if (count > 99) "99+" else count.toString(),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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


