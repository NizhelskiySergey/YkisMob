package com.ykis.mob.ui.screens.chat

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState


data class UserWithLatestMessage(
  val user: UserEntity,
  val latestMessage: MessageEntity
)
/**
 * Компонент списка пользователей с отображением последнего сообщения в каждом чате.
 * Реализует логику подписки на обновления чатов и сортировку по времени.
 */
@Composable
fun UserList(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  userList: List<UserEntity>,
  onUserClick: (UserEntity) -> Unit,
  chatViewModel: ChatViewModel
) {
  // Список пар (Пользователь + Последнее сообщение) для отображения в LazyColumn
  var userWithMessages by remember {
    mutableStateOf(emptyList<UserWithLatestMessage>())
  }

  // Хранилище последних сообщений: ключ — UID пользователя, значение — объект сообщения
  val latestMessages = remember {
    mutableStateOf(mapOf<String, MessageEntity>())
  }

  // Эффект для установки слушателей на каждый чат при изменении списка пользователей
  LaunchedEffect(key1 = userList) {
    userList.forEach { user ->
      // Формируем уникальный ID чата в зависимости от роли (для ОСББ добавляется osbbId)
      val chatUid = if (baseUIState.userRole == UserRole.OsbbUser) {
        "${baseUIState.userRole.codeName}_${baseUIState.osbbId}_${user.uid}"
      } else {
        "${baseUIState.userRole.codeName}_${user.uid}"
      }

      // Подписываемся на обновления последнего сообщения в конкретной ветке Firestore
      chatViewModel.addChatListener(
        chatUid,
        onLastMessageChange = { message ->
          // Обновляем карту сообщений реактивно
          latestMessages.value = latestMessages.value.toMutableMap().apply {
            put(user.uid, message)
          }
        }
      )
    }
  }

  // Эффект для автоматической сортировки списка при получении новых сообщений
  LaunchedEffect(latestMessages.value) {
    val userMessages = userList.map { user ->
      UserWithLatestMessage(
        user = user,
        latestMessage = latestMessages.value[user.uid] ?: MessageEntity()
      )
    }

    // Сортируем: чаты с самыми свежими сообщениями (timestamp) идут вверх
    userWithMessages = userMessages.sortedByDescending { it.latestMessage.timestamp }

    Log.d("YkisMob", "Список чатов обновлен и отсортирован")
  }

  // Отрисовка списка
  LazyColumn(modifier = modifier.fillMaxSize()) {
    items(
      items = userWithMessages,
      key = { it.user.uid } // Ключ для оптимизации рекомпозиции и анимаций
    ) { userWithMessage ->
      val user = userWithMessage.user
      val latestMessage = userWithMessage.latestMessage

      // Исключаем текущего пользователя (админа) из списка собеседников
      if (baseUIState.uid != user.uid) {
        UserListItem(
          user = user, // Передаем объект пользователя
          onUserClick = onUserClick,
          lastMessage = latestMessage, // Передаем последнее сообщение (включая статус isRead)
          currentUserId = baseUIState.uid.toString() // Передаем ID для проверки "свой/чужой"
        )
      }
    }
  }
}

