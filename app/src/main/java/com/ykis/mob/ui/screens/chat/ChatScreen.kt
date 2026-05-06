package com.ykis.mob.ui.screens.chat

import MessageListItem
import android.R.attr.mimeType
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
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

  // Логика определения chatUid.
  // Извлекаем конкретное значение UID, чтобы remember реагировал на смену пользователя
  val targetUid = selectedUser.uid ?: ""

  val chatUid = remember(baseUIState.userRole, targetUid, baseUIState.uid) {
    Log.d("YkisLog", "RootNavGraph.chatUid: [CALC] Role: ${baseUIState.userRole} | TargetUID: $targetUid")
    if (baseUIState.userRole == UserRole.StandardUser) {
      baseUIState.uid.toString()
    } else {
      targetUid // Для админа ТБО это будет qf2p7ogvjn...
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
  navigationType: NavigationType,
  navigateBack: () -> Unit,
  navigateToSendImageScreen: () -> Unit,
  chatUid: String,
  navigateToCameraScreen: () -> Unit,
  navigateToImageDetailScreen: (MessageEntity) -> Unit
) {
  // --- ПОЛУЧЕНИЕ СОСТОЯНИЙ ---
  val messageText by chatViewModel.messageText.collectAsStateWithLifecycle()
  val messageList by chatViewModel.firebaseTest.collectAsStateWithLifecycle()
  val selectedService by chatViewModel.selectedService.collectAsStateWithLifecycle()
  val isLoadingAfterSending by chatViewModel.isLoadingAfterSending.collectAsStateWithLifecycle()
  val aiAssistantResponse by chatViewModel.assistantResponse.collectAsStateWithLifecycle()
  val aiQuickHint by chatViewModel.quickHint.collectAsStateWithLifecycle()
  val isPartnerTyping by chatViewModel.isPartnerTyping.collectAsStateWithLifecycle()
  val isForwardingMode by chatViewModel.isForwardingMode.collectAsStateWithLifecycle()
  val messageToDelete by chatViewModel.messageToDelete.collectAsStateWithLifecycle()
  val editingMessage by chatViewModel.editingMessage.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val listState = rememberLazyListState()

  val myUid = baseUIState.uid.toString()

  // --- 1. ЛОГИКА ФОРМИРОВАНИЯ КОНТЕНТА ---
  val currentChatOsbbId = remember(baseUIState.userRole, baseUIState.osbbId) {
    when (baseUIState.userRole) {
      UserRole.YtkeUser -> 9998
      UserRole.VodokanalUser -> 9999
      UserRole.TboUser -> 9997
      UserRole.OsbbUser -> baseUIState.osbbId ?: 0
      else -> baseUIState.osmdId
    }
  }

  val chatItems = remember(messageList) {
    messageList
      .filter { msg -> !msg.deletedFor.contains(myUid) }
      .groupBy { formatDate(it.timestamp) }
      .flatMap { (date, messages) ->
        listOf(ChatItem.DateHeader(date)) + messages.map { ChatItem.MessageItem(it) }
      }
  }

  // --- 2. ЭФФЕКТЫ ---
  DisposableEffect(Unit) {
    onDispose {

      // 1. Сначала говорим базе, что мы закончили печатать
      chatViewModel.setTypingStatus(false)
      Log.d("YkisLog", "Chat: [DISPOSE] Выход из чата закончили печатать")
      // 2. И только потом чистим пути

      chatViewModel.clearCurrentChatPath()
      Log.d("YkisLog", "Chat: [DISPOSE] Очистка пути чата")
    }
  }

  // КРИТИЧЕСКИЙ ФИКС: Эта загрузка должна срабатывать ТОЛЬКО для жильца.
  // Админ уже вызвал readFromDatabase в методе openChatWithUser при клике в списке.
  LaunchedEffect(chatUid, baseUIState.addressId) {
    val role = baseUIState.userRole

    if (role == UserRole.StandardUser) {
      if (chatUid.isNotEmpty() && baseUIState.addressId > 0) {
        Log.d("YkisLog", "Chat: [INIT_RESIDENT] Загрузка сообщений для о/р: ${baseUIState.addressId}")
        chatViewModel.readFromDatabase(
          role = role,
          senderUid = chatUid,
          osbbId = currentChatOsbbId,
          addressId = baseUIState.addressId
        )
      }
    } else {
      // Для админа просто подтверждаем в логах, что мы на экране
      Log.d("YkisLog", "Chat: [INIT_ADMIN] Экран открыт для ${userEntity.displayName}")
    }
  }


  LaunchedEffect(chatItems.size) {
    if (chatItems.isNotEmpty()) {
      listState.animateScrollToItem(chatItems.size - 1)
    }
  }
  // --- ЛОГИКА ЗАГОЛОВКА ---
  val appBarTitle = remember(baseUIState, selectedService, isForwardingMode) {
    if (isForwardingMode) "Переслати повідомлення"
    else if (baseUIState.userRole == UserRole.StandardUser) {
      if (selectedService?.name == "OSBB") baseUIState.osbb ?: "ОСББ"
      else selectedService?.name ?:""
    } else {
      // Для админа заголовок — это адрес жильца
      userEntity.displayName?.substringBefore("|")?.trim() ?: "Чат"
    }
  }

  val appBarSubtitle = remember(baseUIState, userEntity, isPartnerTyping) {
    if (isPartnerTyping) "друкує..."
    else if (baseUIState.userRole == UserRole.StandardUser) {
      baseUIState.address // Адрес как подзаголовок для жильца
    } else {
      // Имя жильца как подзаголовок для админа
      userEntity.displayName?.substringAfter("|")?.trim()
    }
  }


  // --- 3. UI СТРУКТУРА ---
  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      Column {
        DefaultAppBar(
          title = appBarTitle,
          subtitle = appBarSubtitle, // <--- ПЕРЕДАЕМ АДРЕС СЮДА
          canNavigateBack = true,
          onBackClick = {
            keyboardController?.hide()
            focusManager.clearFocus()
            if (isForwardingMode) chatViewModel.cancelForwarding() else navigateBack()
          },
          navigationType = navigationType
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
      }
    }
    ,
    bottomBar = {
      // Подвал вынесен в bottomBar для правильной работы с клавиатурой
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .navigationBarsPadding()
          .imePadding()
      ) {
        // Подсказки ИИ
        val activeAiText = if (baseUIState.userRole != UserRole.StandardUser) aiQuickHint else aiAssistantResponse
        AnimatedVisibility(visible = !activeAiText.isNullOrBlank()) {
          AiHintCard(
            text = activeAiText ?: "",
            title = if (baseUIState.userRole != UserRole.StandardUser) "Порада диспетчеру" else "Помічник",
            onClose = { chatViewModel.clearAiSuggestion() },
            onApply = {
              Log.d("YkisLog", "Chat: [AI_APPLY] Подсказка вставлена")
              chatViewModel.applyAiHint()
            }
          )
        }

        Surface(tonalElevation = 6.dp) {
          ComposeMessageBox(
            text = messageText,
            onTextChanged = { chatViewModel.onMessageTextChanged(it) },
            onSent = {
              if (editingMessage != null) {
                Log.d("YkisLog", "Chat: [EDIT_SUBMIT] $messageText")
                chatViewModel.updateMessage(messageText)
              } else {
                val curAddrId = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.addressId else userEntity.addressId
                val curAddr = if (baseUIState.userRole == UserRole.StandardUser) baseUIState.address ?: "" else userEntity.displayName ?: ""

                Log.d("YkisLog", "Chat: [MSG_SEND] Addr: $curAddr, ID: $curAddrId")

                chatViewModel.writeToDatabase(
                  chatUid = chatUid,
                  senderUid = myUid,
                  senderDisplayedName = baseUIState.displayName ?: "Користувач",
                  senderLogoUrl = baseUIState.photoUrl,
                  senderAddress = curAddr,
                  addressId = curAddrId,
                  imageUrl = null,
                  fileUrl = null,
                  fileName = null,
                  osbbId = currentChatOsbbId,
                  role = baseUIState.userRole,
                  onComplete = {
                    Log.d("YkisLog", "Chat: [SEND_COMPLETE] Поле очищено")
                    chatViewModel.clearAiSuggestion()
                  },
                  recipientTokens = userEntity.tokens
                )
              }
            },
            onImageSent = { uri ->
              val mime = context.contentResolver.getType(uri) ?: ""
              Log.d("YkisLog", "Chat: [ATTACH] Uri: $uri, Mime: $mime")
              chatViewModel.setSelectedImageUri(uri)
              if (mime.contains("image")) {
                Log.d("YkisLog", "Chat: [AI_START] Авто-анализ фото")
                chatViewModel.analyzePhotoWithGemini(uri, context, baseUIState.address ?: "")
              }
              navigateToSendImageScreen()
            },
            onAiClick = {
              Log.d("YkisLog", "Chat: [AI_CLICK] Текстовый помощник")
              if (messageText.isNotBlank()) chatViewModel.askAssistant(messageText)
            },
            onCameraClick = {
              Log.d("YkisLog", "Chat: [CAMERA_CLICK]")
              navigateToCameraScreen()
            },
            isLoading = isLoadingAfterSending,
            canSend = messageText.isNotBlank() || editingMessage != null
          )
        }
      }
    }
  ) { innerPadding ->
    // Контент чата (Список сообщений)
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding), // Scaffold сам даст отступ сверху и снизу
      state = listState,
      contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      chatItems.forEach { chatItem ->
        when (chatItem) {
          is ChatItem.DateHeader -> stickyHeader { DateChip(date = chatItem.date) }
          is ChatItem.MessageItem -> item(key = "${chatItem.message.id}_${chatItem.message.timestamp}") {
            MessageListItem(
              uid = myUid,
              isUserAdmin = baseUIState.userRole != UserRole.StandardUser,
              messageEntity = chatItem.message,
              onLongClick = { chatViewModel.showDeleteConfirmation(chatItem.message) },
              onClick = {
                Log.d("YkisLog", "Chat: [IMAGE_CLICK] Открытие детального просмотра")
                keyboardController?.hide()
                navigateToImageDetailScreen(chatItem.message)
              },
              onFileClick = { fileUrl ->
                Log.d("YkisLog", "Chat: [FILE_OPEN] URL: $fileUrl")
                try { uriHandler.openUri(fileUrl) } catch (e: Exception) { Log.e("YkisLog", "Uri error: ${e.message}") }
              }
            )
          }
        }
      }
    }
  }



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
