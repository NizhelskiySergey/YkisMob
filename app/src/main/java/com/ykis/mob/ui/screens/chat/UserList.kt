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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState

data class UserWithLatestMessage(
  val user: UserEntity,
  val latestMessage: MessageEntity,
  val unreadCount: Int,
  val chatId: String
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
  val unreadCounts by chatViewModel.unreadCounts.collectAsStateWithLifecycle()

  // Оптимизируем список: вычисляем всё один раз при изменении данных
  val userWithMessages = remember(userList, latestMessages, unreadCounts) {
    userList.map { user ->
      // Генерируем правильный ключ чата
      val chatId = if (baseUIState.userRole == UserRole.OsbbUser) {
        "OSBB_${baseUIState.osbbId}_${user.addressId}_${user.uid}"
      } else {
        "${baseUIState.userRole.codeName.name}_${user.addressId}_${user.uid}"
      }

      val lastMsg = latestMessages[chatId] ?: MessageEntity(text = "Нет сообщений", timestamp = 0L)
      val unreadCount = unreadCounts[chatId] ?: 0

      val stableDisplayName = lastMsg.senderAddress.ifBlank {
        user.displayName ?: "Жилец"
      }

      UserWithLatestMessage(
        user = user.copy(displayName = stableDisplayName),
        latestMessage = lastMsg,
        unreadCount = unreadCount,
        chatId = chatId
      )
    }.sortedWith(
      compareByDescending<UserWithLatestMessage> { it.unreadCount > 0 } // Сначала непрочитанные
        .thenByDescending { it.latestMessage.timestamp }             // Затем по времени
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(vertical = 8.dp)
  ) {
    items(
      items = userWithMessages,
      key = { it.chatId } // Используем уникальный chatId как ключ для стабильности списка
    ) { item ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 2.dp)
      ) {
        UserListItem(
          it = item.user,
          onUserClick = onUserClick,
          lastMessage = item.latestMessage,
          currentUid = baseUIState.uid.toString()
        )

        // Красивый индикатор (Badge)
        if (item.unreadCount > 0) {
          Surface(
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .padding(end = 16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error,
            tonalElevation = 6.dp,
            shadowElevation = 2.dp
          ) {
            Text(
              text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
              color = MaterialTheme.colorScheme.onError,
              fontWeight = FontWeight.ExtraBold
            )
          }
        }
      }
    }
  }
}

// Дополнительный вспомогательный класс для чистоты кода




