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

  // 1. Оптимизированный расчет списка
  val userWithMessages = remember<List<UserWithLatestMessage>>(userList, latestMessages, unreadCounts) {
    val methodName = "UserList.Mapping"
    Log.d("YkisLog", "$methodName: [START] Пересчет для ${userList.size} строк")

    userList.map { user ->
      // ГАРАНТИРУЕМ точное совпадение ключей с ViewModel (trackUserIdentifiersWithRole)
      val chatId = when (baseUIState.userRole) {
        UserRole.VodokanalUser -> "WATER_SERVICE_9999_${user.addressId}_${user.uid}"
        UserRole.YtkeUser      -> "WARM_SERVICE_9998_${user.addressId}_${user.uid}"
        UserRole.TboUser       -> "GARBAGE_SERVICE_9997_${user.addressId}_${user.uid}"
        UserRole.OsbbUser      -> "OSBB_${baseUIState.osbbId}_${user.addressId}_${user.uid}"
        else                   -> "UNKNOWN_${user.addressId}_${user.uid}"
      }

      val lastMsg = latestMessages[chatId]
      val count = unreadCounts[chatId] ?: 0

      // Если сообщения еще нет в кэше, создаем временный объект, чтобы не было пустоты
      val safeMsg = lastMsg ?: MessageEntity(text = "", timestamp = 0L)

      // Важно: если в сообщении пришел адрес (senderAddress), приоритет ему
      val stableDisplayName = if (!safeMsg.senderAddress.isNullOrBlank()) {
        safeMsg.senderAddress
      } else {
        user.displayName ?: "Користувач (о/р ${user.addressId})"
      }

      if (lastMsg != null) {
        Log.v("YkisLog", "$methodName: [MATCH_OK] Ключ: $chatId | Текст: ${lastMsg.text?.take(10)}")
      }

      UserWithLatestMessage(
        user = user.copy(displayName = stableDisplayName),
        latestMessage = safeMsg,
        unreadCount = count,
        chatId = chatId
      )
    }.sortedWith(
      // Сначала те, где есть непрочитанные, потом самые свежие по времени
      compareByDescending<UserWithLatestMessage> { it.unreadCount > 0 }
        .thenByDescending { it.latestMessage.timestamp }
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(vertical = 8.dp)
  ) {
    if (userWithMessages.isEmpty()) {
      item {
        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
          Text("Активних чатів не знайдено", color = MaterialTheme.colorScheme.outline)
        }
      }
    }

    items(
      items = userWithMessages,
      key = { it.chatId } // Ключ позволяет Compose анимировать перемещение строк
    ) { item ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .animateItem() // Добавляет плавность при перестановке (новые чаты летят вверх)
          .padding(horizontal = 8.dp, vertical = 2.dp)
      ) {
        UserListItem(
          it = item.user,
          onUserClick = onUserClick,
          // Передаем null, если сообщения реально нет (для отображения "Нет сообщений")
          lastMessage = if (item.latestMessage.timestamp > 0L) item.latestMessage else null,
          currentUid = baseUIState.uid.toString()
        )

        // БЕЙДЖ (Красный индикатор)
        if (item.unreadCount > 0) {
          Surface(
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .padding(end = 16.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error,
            tonalElevation = 6.dp
          ) {
            Text(
              text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
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




