package com.ykis.mob.ui.screens.chat

import MessageListItem
import android.R.attr.onClick
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ykis.mob.R
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import com.ykis.mob.ui.navigation.CameraScreenDest
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.navigation.ImageDetailScreenDest
import com.ykis.mob.ui.navigation.SendImageScreenDest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(timestamp: Long): String {
  val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
  return sdf.format(Date(timestamp))
}

sealed class ChatItem {
  data class DateHeader(val date: String) : ChatItem()
  data class MessageItem(val message: MessageEntity) : ChatItem()

}
@Composable
fun ChatScreenStateful(
  chatViewModel: ChatViewModel,
  baseUIState: BaseUIState,
  navController: NavHostController,
) {
  val selectedUser by chatViewModel.selectedUser.collectAsStateWithLifecycle()
  val selectedService by chatViewModel.selectedService.collectAsStateWithLifecycle()

  // Логика определения chatUid
  val chatUid = remember(baseUIState.userRole, selectedUser, selectedService) {
    when (baseUIState.userRole) {
      UserRole.YtkeUser -> "9997"
      UserRole.VodokanalUser -> "9998"
      UserRole.TboUser -> "9999"
      UserRole.OsbbUser -> selectedUser?.uid ?: ""
      UserRole.StandardUser -> baseUIState.uid.toString()
      else -> ""
    }
  }

  // ВЫЗОВ В СТРОГОМ СООТВЕТСТВИИ С КОНСТРУКТОРОМ ChatScreenContent
  ChatScreenContent(
    modifier = Modifier,                         // 0. Modifier (если он первый в функции)
    userEntity = selectedUser ?: UserEntity(),   // 1. userEntity
    chatViewModel = chatViewModel,               // 2. chatViewModel
    baseUIState = baseUIState,                   // 3. baseUIState
    navigateBack = { navController.popBackStack() }, // 4. navigateBack
    navigateToSendImageScreen = { navController.navigate(SendImageScreenDest.route) }, // 5
    chatUid = chatUid,                           // 6. chatUid
    navigateToCameraScreen = { navController.navigate(CameraScreenDest.route) }, // 7
    navigateToImageDetailScreen = { message ->   // 8
      chatViewModel.setSelectedMessage(message)
      navController.navigate(ImageDetailScreenDest.route)
    }
  )
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreenContent(
  modifier: Modifier = Modifier,
  userEntity: UserEntity,
  chatViewModel: ChatViewModel,
  baseUIState: BaseUIState,
  navigateBack: () -> Unit,
  navigateToSendImageScreen: () -> Unit,
  chatUid: String,
  navigateToCameraScreen: () -> Unit,
  navigateToImageDetailScreen: (MessageEntity) -> Unit
) {
  val messageText by chatViewModel.messageText.collectAsStateWithLifecycle()
  val messageList by chatViewModel.firebaseTest.collectAsStateWithLifecycle()
  val selectedService by chatViewModel.selectedService.collectAsStateWithLifecycle()
  val isLoadingAfterSending by chatViewModel.isLoadingAfterSending.collectAsStateWithLifecycle()
  val aiAssistantResponse by chatViewModel.assistantResponse.collectAsStateWithLifecycle()
  val aiQuickHint by chatViewModel.quickHint.collectAsStateWithLifecycle()

  // Подписываемся на состояние удаления
  val messageToDelete by chatViewModel.messageToDelete.collectAsStateWithLifecycle()

  val listState = rememberLazyListState()

  // 1. Определение эффективного OSBB ID
  val currentChatOsbbId = remember(baseUIState.userRole, baseUIState.osbbId) {
    when (baseUIState.userRole) {
      UserRole.YtkeUser -> 9997
      UserRole.VodokanalUser -> 9998
      UserRole.TboUser -> 9999
      UserRole.OsbbUser -> baseUIState.osbbId ?: 0
      else -> baseUIState.osmdId
    }
  }

  // 2. СБРОС ПУТИ ПРИ ВЫХОДЕ
  DisposableEffect(Unit) {
    onDispose {
      Log.d("YkisLog", "ChatScreen: [DISPOSE] Сброс currentChatPath")
      chatViewModel.clearCurrentChatPath()
    }
  }

  // 3. ПЕРВИЧНАЯ ПОДГРУЗКА (только жилец)
  LaunchedEffect(key1 = chatUid) {
    if (chatUid.isNotEmpty() && baseUIState.userRole == UserRole.StandardUser) {
      chatViewModel.readFromDatabase(
        role = baseUIState.userRole,
        senderUid = chatUid,
        osbbId = currentChatOsbbId,
        addressId = baseUIState.addressId
      )
    }
  }

  // 4. ДИАЛОГ УДАЛЕНИЯ
  if (messageToDelete != null) {
    AlertDialog(
      onDismissRequest = { chatViewModel.dismissDeleteDialog() },
      title = { Text(text = "Удалить сообщение?") },
      text = { Text(text = "Это сообщение исчезнет у всех участников чата. Это действие нельзя отменить.") },
      confirmButton = {
        TextButton(onClick = { chatViewModel.confirmDeletion() }) {
          Text("Удалить", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { chatViewModel.dismissDeleteDialog() }) {
          Text("Отмена")
        }
      }
    )
  }

  val chatItems = remember(messageList) {
    messageList.groupBy { formatDate(it.timestamp) }
      .flatMap { (date, messages) ->
        listOf(ChatItem.DateHeader(date)) + messages.map { ChatItem.MessageItem(it) }
      }
  }

  LaunchedEffect(key1 = chatItems.size) {
    if (chatItems.isNotEmpty()) {
      listState.animateScrollToItem(chatItems.size - 1)
    }
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer
  ) {
    Column(modifier = Modifier.fillMaxSize()) {

      // ЛОГИКА ЗАГОЛОВКА
      val appBarTitle = remember(baseUIState, selectedService, userEntity) {
        if (baseUIState.userRole == UserRole.StandardUser) {
          if (selectedService.name == ContentDetail.OSBB.name) {
            "Чат ${baseUIState.address}"
          } else {
            selectedService.name
          }
        } else {
          val parts = userEntity.displayName?.split("|") ?: emptyList()
          val addr = parts.getOrNull(0)?.trim() ?: userEntity.displayName ?: "Чат"
          val name = parts.getOrNull(1)?.trim() ?: ""
          if (name.isNotEmpty()) "$addr\n$name" else addr
        }
      }

      DefaultAppBar(
        title = appBarTitle,
        canNavigateBack = true,
        onBackClick = navigateBack,
      )

      LazyColumn(
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        chatItems.forEach { chatItem ->
          when (chatItem) {
            is ChatItem.DateHeader -> stickyHeader(key = chatItem.date) {
              DateChip(date = chatItem.date)
            }
            is ChatItem.MessageItem -> item(key = chatItem.message.id) {
              MessageListItem(
                uid = baseUIState.uid.toString(),
                isUserAdmin = baseUIState.userRole != UserRole.StandardUser,
                messageEntity = chatItem.message,
                onLongClick = {
                  Log.d("YkisLog", "ChatScreen: Запрос диалога удаления для ${chatItem.message.id}")
                  chatViewModel.showDeleteConfirmation(chatItem.message)
                },
                onClick = { navigateToImageDetailScreen(chatItem.message) },
              )
            }
          }
        }
      }

      // ПАНЕЛЬ ВВОДА
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .navigationBarsPadding()
          .imePadding()
      ) {
        val activeAiText = if (baseUIState.userRole != UserRole.StandardUser) aiQuickHint else aiAssistantResponse

        AnimatedVisibility(visible = !activeAiText.isNullOrBlank()) {
          AiHintCard(
            text = activeAiText ?: "",
            title = if (baseUIState.userRole != UserRole.StandardUser) "Совет диспетчеру" else "Помощник",
            onClose = { chatViewModel.clearAiSuggestion() },
            onApply = { chatViewModel.applyAiHint() }
          )
        }

        Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
          ComposeMessageBox(
            text = messageText,
            onTextChanged = { chatViewModel.onMessageTextChanged(it) },
            onSent = {
              val currentAddressId = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.addressId else userEntity.addressId

              Log.d("YkisLog", "ChatScreen: [SEND] Recipient tokens: ${userEntity.tokens.size}")

              chatViewModel.writeToDatabase(
                chatUid = chatUid,
                senderUid = baseUIState.uid.toString(),
                senderDisplayedName = baseUIState.displayName ?: "Диспетчер",
                senderLogoUrl = baseUIState.photoUrl,
                role = baseUIState.userRole,
                senderAddress = if (baseUIState.userRole == UserRole.StandardUser) {
                  baseUIState.displayName ?: ""
                } else {
                  userEntity.displayName ?: "Служба"
                },
                addressId = currentAddressId,
                imageUrl = null,
                osbbId = currentChatOsbbId,
                recipientTokens = userEntity.tokens,
                onComplete = { chatViewModel.clearAiSuggestion() }
              )
            },
            onImageSent = {
              chatViewModel.setSelectedImageUri(it)
              navigateToSendImageScreen()
            },
            onAiClick = { if (messageText.isNotBlank()) chatViewModel.askAssistant(messageText) },
            onCameraClick = navigateToCameraScreen,
            isLoading = isLoadingAfterSending,
            canSend = messageText.isNotBlank()
          )
        }
      }
    }
  }
}



@Composable
fun AiHintCard(
  text: String,
  title: String,
  onClose: () -> Unit,
  onApply: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
    shape = RoundedCornerShape(16.dp),
    tonalElevation = 4.dp,
    shadowElevation = 2.dp
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Icon(
          imageVector = Icons.Default.SmartToy,
          contentDescription = "AI Hint",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        IconButton(
          onClick = onClose,
          modifier = Modifier.size(24.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Закрыть",
            modifier = Modifier.graphicsLayer(scaleX = 0.7f, scaleY = 0.7f)
          )
        }
      }

      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 4.dp)
      )

      Text(
        text = "Использовать ответ",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
          .align(Alignment.End)
          .clickable { onApply() }
          .padding(top = 4.dp)
      )
    }
  }
}


@Composable
fun DateChip(date: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
      shape = RoundedCornerShape(16.dp)
    ) {
      Text(
        text = date,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer
      )
    }
  }
}
