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
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.ykis.mob.ui.navigation.NavigationType
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
  navigationType: NavigationType
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
    baseUIState = baseUIState,
    navigationType = navigationType,// 3. baseUIState
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
  navigationType: NavigationType, // ДОБАВИТЬ ЭТО
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
  val isPartnerTyping by chatViewModel.isPartnerTyping.collectAsStateWithLifecycle()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val isForwardingMode by chatViewModel.isForwardingMode.collectAsStateWithLifecycle()


  // Редактирование и удаление
  val messageToDelete by chatViewModel.messageToDelete.collectAsStateWithLifecycle()
  val editingMessage by chatViewModel.editingMessage.collectAsStateWithLifecycle()

  val listState = rememberLazyListState()
  val lastMessageId = remember(messageList) { messageList.lastOrNull()?.id }

  // 1. Конфигурация чата
  val currentChatOsbbId = remember(baseUIState.userRole, baseUIState.osbbId) {
    when (baseUIState.userRole) {
      UserRole.YtkeUser -> 9997
      UserRole.VodokanalUser -> 9998
      UserRole.TboUser -> 9999
      UserRole.OsbbUser -> baseUIState.osbbId ?: 0
      else -> baseUIState.osmdId
    }
  }
// В ChatScreenContent.kt
  val myUid = baseUIState.uid.toString() // Берем ваш текущий UID

  val chatItems = remember(messageList) {
    messageList
      // 1. Сначала фильтруем: убираем сообщения, где ваш UID есть в списке удаленных
      .filter { msg ->
        msg.deletedFor == null || !msg.deletedFor.contains(myUid)
      }
      // 2. Группируем по датам только оставшиеся сообщения
      .groupBy { formatDate(it.timestamp) }
      .flatMap { (date, messages) ->
        listOf(ChatItem.DateHeader(date)) + messages.map { ChatItem.MessageItem(it) }
      }
  }


  DisposableEffect(Unit) {
    onDispose { chatViewModel.clearCurrentChatPath() }
  }

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

  // 2. Диалог действий (Удалить/Редактировать)
  if (messageToDelete != null) {
    AlertDialog(
      onDismissRequest = { chatViewModel.dismissDeleteDialog() },
      title = {
        Text(
          text = "Действия с сообщением",
          style = MaterialTheme.typography.titleMedium
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // 1. РЕДАКТИРОВАТЬ (Только свои)
          val isMyMessage = messageToDelete?.senderUid == baseUIState.uid.toString()


          if (isMyMessage && messageToDelete?.imageUrl == null) {
            TextButton(
              onClick = {
                chatViewModel.startEditing(messageToDelete!!)
                chatViewModel.dismissDeleteDialog()
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.Edit, contentDescription = null)
              Spacer(Modifier.width(12.dp))
              Text("Редактировать", modifier = Modifier.weight(1f))
            }
          }

          // 2. ПЕРЕСЛАТЬ (Любое сообщение)
          // В блоке AlertDialog для пересылки
          TextButton(
            onClick = {
              // 1. Запоминаем сообщение
              chatViewModel.startForwarding(messageToDelete!!)

              // 2. Закрываем диалог
              chatViewModel.dismissDeleteDialog()

              // 3. УХОДИМ ИЗ ЧАТА (чтобы попасть в список пользователей)
              Log.d("YkisLog", "UI: Сообщение выбрано, переход к списку получателей")
              navigateBack() // Или navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.AutoMirrored.Filled.Reply, modifier = Modifier.graphicsLayer(scaleX = -1f), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Переслать", modifier = Modifier.weight(1f))
          }


          HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

          // 3. УДАЛИТЬ У МЕНЯ
          TextButton(
            onClick = {
              chatViewModel.deleteForMe(messageToDelete!!.id)
              chatViewModel.dismissDeleteDialog()
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Удалить у себя", modifier = Modifier.weight(1f))
          }

          // 4. УДАЛИТЬ ДЛЯ ВСЕХ (Только свои)
          if (isMyMessage) {
            TextButton(
              onClick = {
                chatViewModel.confirmDeletion()
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Spacer(Modifier.width(12.dp))
              Text("Удалить для всех", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { chatViewModel.dismissDeleteDialog() }) {
          Text("Отмена")
        }
      }
    )
  }




  LaunchedEffect(key1 = lastMessageId) {
    if (lastMessageId != null && chatItems.isNotEmpty()) {
      listState.animateScrollToItem(chatItems.size - 1)
    }
  }



  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer
  ) {
    Column(modifier = Modifier.fillMaxSize()) {

      // --- ШАПКА ---
      val appBarTitle = remember(baseUIState, selectedService, userEntity, isForwardingMode) {
        if (isForwardingMode) {
          "Переслать сообщение" // Явный заголовок для режима пересылки
        } else if (baseUIState.userRole == UserRole.StandardUser) {
          if (selectedService.name == ContentDetail.OSBB.name) "Чат ${baseUIState.address}"
          else selectedService.name
        } else {
          val parts = userEntity.displayName?.split("|") ?: emptyList()
          val addr = parts.getOrNull(0)?.trim() ?: userEntity.displayName ?: "Чат"
          val name = parts.getOrNull(1)?.trim() ?: ""
          if (name.isNotEmpty()) "$addr\n$name" else addr
        }
      }

      DefaultAppBar(
        title = appBarTitle,
        subtitle = if (isPartnerTyping) "печатает..." else null,
        canNavigateBack = true,
        onBackClick = {
          // КРИТИЧЕСКИ ВАЖНО: Прячем клавиатуру и сбрасываем фокус перед уходом
          keyboardController?.hide()
          focusManager.clearFocus()

          if (isForwardingMode) {
            chatViewModel.cancelForwarding()
          } else {
            navigateBack()
          }
        },
        navigationType = navigationType
      )

      HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

      // --- СПИСОК СООБЩЕНИЙ ---
      val listState = rememberLazyListState()

      // Авто-скролл при появлении новых сообщений
      LaunchedEffect(chatItems.size) {
        if (chatItems.isNotEmpty()) {
          listState.animateScrollToItem(chatItems.size - 1)
        }
      }

      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        chatItems.forEach { chatItem ->
          when (chatItem) {
            is ChatItem.DateHeader -> {
              stickyHeader(key = chatItem.date) {
                DateChip(date = chatItem.date)
              }
            }
            is ChatItem.MessageItem -> {
              item(key = chatItem.message.id) {
                MessageListItem(
                  uid = baseUIState.uid.toString(),
                  isUserAdmin = baseUIState.userRole != UserRole.StandardUser,
                  messageEntity = chatItem.message,
                  onLongClick = { chatViewModel.showDeleteConfirmation(chatItem.message) },
                  onClick = {
                    // Прячем клавиатуру при клике на фото для полноэкранного режима
                    keyboardController?.hide()
                    navigateToImageDetailScreen(chatItem.message)
                  }
                )
              }
            }
          }
        }
      }

      // --- ПОДВАЛ (Ввод + Редактирование) ---
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .navigationBarsPadding()
          .imePadding() // Клавиатура будет плавно поднимать подвал
      ) {
        // Плашка редактирования
        AnimatedVisibility(visible = editingMessage != null) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp)
              .background(
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
              )
              .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
              text = "Редактирование",
              modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
              style = MaterialTheme.typography.labelMedium
            )
            IconButton(onClick = { chatViewModel.cancelEditing() }, modifier = Modifier.size(24.dp)) {
              Icon(Icons.Default.Close, contentDescription = null)
            }
          }
        }

        // ИИ Помощник
        val activeAiText = if (baseUIState.userRole != UserRole.StandardUser) aiQuickHint else aiAssistantResponse
        AnimatedVisibility(visible = !activeAiText.isNullOrBlank()) {
          AiHintCard(
            text = activeAiText ?: "",
            title = if (baseUIState.userRole != UserRole.StandardUser) "Совет диспетчеру" else "Помощник",
            onClose = { chatViewModel.clearAiSuggestion() },
            onApply = { chatViewModel.applyAiHint() }
          )
        }

        // Поле ввода
        Surface(tonalElevation = 6.dp, shadowElevation = 8.dp) {
          ComposeMessageBox(
            text = messageText,
            onTextChanged = { chatViewModel.onMessageTextChanged(it) },
            onSent = {
              if (editingMessage != null) {
                chatViewModel.updateMessage(messageText)
              } else {
                val currentAddressId = if (baseUIState.userRole == UserRole.StandardUser)
                  baseUIState.addressId else userEntity.addressId

                chatViewModel.writeToDatabase(
                  chatUid = chatUid,
                  senderUid = baseUIState.uid.toString(),
                  senderDisplayedName = baseUIState.displayName ?: "Диспетчер",
                  senderLogoUrl = baseUIState.photoUrl,
                  role = baseUIState.userRole,
                  senderAddress = if (baseUIState.userRole == UserRole.StandardUser)
                    baseUIState.displayName ?: "" else userEntity.displayName ?: "Служба",
                  addressId = currentAddressId,
                  imageUrl = null,
                  osbbId = currentChatOsbbId,
                  recipientTokens = userEntity.tokens,
                  onComplete = { chatViewModel.clearAiSuggestion() }
                )
              }
            },
            onImageSent = { chatViewModel.setSelectedImageUri(it); navigateToSendImageScreen() },
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
