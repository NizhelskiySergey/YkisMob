package com.ykis.mob.ui.screens.chat

import android.util.Log
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

@Composable
fun UserList(
  modifier: Modifier = Modifier,
  baseUIState: BaseUIState,
  userList: List<UserEntity>,
  onUserClick: (UserEntity) -> Unit,
  chatViewModel: ChatViewModel
) {
  var userWithMessages by remember {
    mutableStateOf(emptyList<UserWithLatestMessage>())
  }

  // Map to hold latest messages
  val latestMessages = remember {
    mutableStateOf(mapOf<String, MessageEntity>())
  }

  LaunchedEffect(key1 = userList) {
    userList.forEach { user ->
      val chatUid = if(baseUIState.userRole == UserRole.OsbbUser){
        "${baseUIState.userRole.codeName}_${baseUIState.osbbRoleId}_${user.uid}"
      }else "${baseUIState.userRole.codeName}_${user.uid}"
      chatViewModel.addChatListener(
        chatUid,
        onLastMessageChange = { message ->
          // Update the map with the latest message
          latestMessages.value = latestMessages.value.toMutableMap().apply {
            put(user.uid, message)
          }
        }
      )
    }
  }

  LaunchedEffect(latestMessages.value) {
    val userMessages = userList.map { user ->
      UserWithLatestMessage(
        user = user,
        latestMessage = latestMessages.value[user.uid] ?: MessageEntity()
      )
    }
    userWithMessages = userMessages.sortedByDescending{ it.latestMessage.timestamp }
    Log.d("desc_test" , userMessages.map { it.latestMessage }.toString())
    Log.d("desc_test" , "sorted: ${ userMessages.sortedByDescending{ it.latestMessage.timestamp }.map { it.latestMessage.text }}")
  }

  LazyColumn(modifier = modifier) {
    items(
      items = userWithMessages,
      key = { it.user.uid }
    ) { userWithMessage ->
      val user = userWithMessage.user
      val latestMessage = userWithMessage.latestMessage
      if (baseUIState.uid != user.uid) {
        UserListItem(it = user, onUserClick = onUserClick, lastMessage = latestMessage)
      }
    }
  }
}
