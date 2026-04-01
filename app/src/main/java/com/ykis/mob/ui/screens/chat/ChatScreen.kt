package com.ykis.mob.ui.screens.chat

import MessageListItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.appbars.DefaultAppBar
import kotlinx.coroutines.launch
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
}@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
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

  // AI состояния
  val aiAssistantResponse by chatViewModel.assistantResponse.collectAsStateWithLifecycle()
  val aiQuickHint by chatViewModel.quickHint.collectAsStateWithLifecycle()

  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  var showDeleteMessageDialog by rememberSaveable { mutableStateOf(false) }
  var selectedMessageId by rememberSaveable { mutableStateOf("") }

  // 1. Инициализация чата
  LaunchedEffect(key1 = chatUid) {
    chatViewModel.readFromDatabase(
      role = baseUIState.userRole,
      senderUid = chatUid,
      osbbId = if (baseUIState.userRole == UserRole.OsbbUser) baseUIState.osbbId ?: 0 else baseUIState.osmdId
    )
  }

  // 2. Группировка сообщений
  val chatItems = remember(messageList) {
    messageList.groupBy { formatDate(it.timestamp) }
      .flatMap { (date, messages) ->
        listOf(ChatItem.DateHeader(date)) + messages.map { ChatItem.MessageItem(it) }
      }
  }

  // 3. Автоскролл
  LaunchedEffect(key1 = chatItems.size) {
    if (chatItems.isNotEmpty()) {
      listState.animateScrollToItem(chatItems.size - 1)
    }
  }

  Surface(
    modifier = Modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      DefaultAppBar(
        title = if (baseUIState.userRole == UserRole.StandardUser) "Чат ${selectedService.name}"
        else userEntity.displayName ?: userEntity.email.toString(),
        canNavigateBack = true,
        onBackClick = navigateBack,
      )

      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .padding(horizontal = 4.dp),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        chatItems.forEach { chatItem ->
          when (chatItem) {
            is ChatItem.DateHeader -> {
              stickyHeader(key = chatItem.date) { DateChip(date = chatItem.date) }
            }
            is ChatItem.MessageItem -> {
              item(key = chatItem.message.id) {
                MessageListItem(
                  uid = baseUIState.uid.toString(),
                  messageEntity = chatItem.message,
                  onLongClick = {
                    selectedMessageId = chatItem.message.id
                    showDeleteMessageDialog = true
                  },
                  onClick = { navigateToImageDetailScreen(chatItem.message) }
                )
              }
            }
          }
        }
      }

      // БЛОК ВВОДА С AI ПОДСКАЗКОЙ
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
      ) {
        // 1. ПЛАВАЮЩАЯ КАРТОЧКА AI
        val activeAiText = if (baseUIState.userRole == UserRole.OsbbUser) aiQuickHint else aiAssistantResponse

        AnimatedVisibility(
          visible = !activeAiText.isNullOrBlank(),
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut()
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
                  text = if (baseUIState.userRole == UserRole.OsbbUser) "Совет диспетчеру" else "Помощник ОСББ",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                  onClick = { chatViewModel.clearAiSuggestion() },
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
                text = activeAiText ?: "",
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
                  .clickable { chatViewModel.applyAiHint() }
                  .padding(top = 4.dp)
              )
            }
          }
        }

        // ПОЛЕ ВВОДА
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 6.dp,
          shadowElevation = 8.dp
        ) {
          Box(modifier = Modifier.navigationBarsPadding()) {
            ComposeMessageBox(
              onSent = {
                chatViewModel.writeToDatabase(
                  chatUid = chatUid,
                  senderUid = baseUIState.uid.toString(),
                  senderDisplayedName = baseUIState.displayName ?: baseUIState.email.toString(),
                  senderLogoUrl = baseUIState.photoUrl,
                  role = baseUIState.userRole,
                  senderAddress = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.address ?: "" else "",
                  imageUrl = null,
                  osbbId = if (baseUIState.userRole == UserRole.OsbbUser) baseUIState.osbbId ?: 0 else baseUIState.osmdId,
                  recipientTokens = userEntity.tokens,
                  onComplete = {
                    // Очищаем AI подсказки сразу
                    chatViewModel.clearAiSuggestion()

                    // Безопасный скролл к последнему элементу
                    coroutineScope.launch {
                      if (chatItems.isNotEmpty()) {
                        val lastIndex = chatItems.size - 1
                        if (lastIndex >= 0) {
                          listState.animateScrollToItem(lastIndex)
                        }
                      }
                    }
                  }
                )

              },
              onImageSent = {
                chatViewModel.setSelectedImageUri(it)
                navigateToSendImageScreen()
              },
              onAiClick = {
                if (messageText.isNotBlank()) {
                  chatViewModel.askAssistant(messageText)
                }
              },
              onCameraClick = navigateToCameraScreen,
              text = messageText,
              onTextChanged = { chatViewModel.onMessageTextChanged(it) },
              isLoading = isLoadingAfterSending,
              canSend = messageText.isNotBlank()
            )
          }
        }
      }
    }
  }

  if (showDeleteMessageDialog) {
    DeleteMessageDialog(
      onDismiss = { showDeleteMessageDialog = false },
      onConfirm = {
        chatViewModel.deleteMessageFromDatabase(
          senderUid = chatUid,
          messageId = selectedMessageId,
          role = baseUIState.userRole,
          osbbId = if (baseUIState.userRole == UserRole.OsbbUser) baseUIState.osbbId ?: 0 else baseUIState.osmdId
        )
        showDeleteMessageDialog = false
      }
    )
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
