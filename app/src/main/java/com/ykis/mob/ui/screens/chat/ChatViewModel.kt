package com.ykis.mob.ui.screens.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.ai.type.content
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.squareup.moshi.Json
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.service.impl.ChatRepository
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class SendNotificationArguments(
  @Json(name = "recipient_token")
  val recipientTokens: List<String>,
  val title: String,
  val body: String
)

data class ServiceWithCodeName(
  val name: String = "",
  val codeName: String = ""
)

data class MessageEntity(
  val id: String = "",
  val senderUid: String = "",
  val senderDisplayedName: String = "",
  val senderLogoUrl: String? = null,
  val senderAddress: String = "",
  val text: String = "",
  val imageUrl: String? = null,
  val timestamp: Long = 0L
)

data class UserEntity(
  val uid: String = "",
  val photoUrl: String? = "",
  val createdAt: Timestamp? = null,
  val password: String? = "",
  val displayName: String? = "",
  val email: String? = "",
  val osbbRoleId: Int? = null,
  val tokens: List<String> = emptyList()
)

fun mapToUserEntity(uid: String, map: Map<String, Any>): UserEntity {
  return UserEntity(
    uid = uid,
    photoUrl = map["photoUrl"] as String?,
    createdAt = map["createdAt"] as Timestamp?,
    password = map["password"] as String?,
    displayName = map["displayName"] as String?,
    email = map["email"] as String?,
    tokens = map["fcmTokens"] as List<String>? ?: emptyList()
  )
}

class ChatViewModel(
  private val chatRepo: ChatRepository,
  logService: LogService
) : BaseViewModel(logService) { // УДАЛИЛИ KoinComponent


    // Перенаправляем ссылки на репозиторий
    private val chatsReference by lazy { chatRepo.realtime.getReference("chats") }
//    private val userDatabase by lazy { chatRepo.firestore }
//    private val storageReference by lazy { chatRepo.storage.reference }
private val generativeModel by lazy { chatRepo.aiModel }
  private val _assistantResponse = MutableStateFlow<String?>(null)
  val assistantResponse = _assistantResponse.asStateFlow()

  // Для диспетчера: быстрая подсказка на основе входящего сообщения
  private val _quickHint = MutableStateFlow<String?>(null)
  val quickHint = _quickHint.asStateFlow()



  private val listeners = mutableMapOf<DatabaseReference, ValueEventListener>()


  private val _firebaseTest = MutableStateFlow<List<MessageEntity>>(emptyList())
  val firebaseTest = _firebaseTest.asStateFlow()

  private val _messageText = MutableStateFlow("")
  val messageText = _messageText.asStateFlow()

  private val _userList = MutableStateFlow<List<UserEntity>>(emptyList())
  val userList = _userList.asStateFlow()

  private val _selectedUser = MutableStateFlow<UserEntity>(UserEntity())
  val selectedUser = _selectedUser.asStateFlow()

  private val _selectedService = MutableStateFlow(ServiceWithCodeName())
  val selectedService = _selectedService.asStateFlow()

  private val _userIdentifiersWithRole = MutableStateFlow<List<String>>(emptyList())
  val userIdentifiersWithRole = _userIdentifiersWithRole.asStateFlow()

  private val _selectedImageUri = MutableStateFlow(Uri.EMPTY)
  val selectedImageUri = _selectedImageUri.asStateFlow()

  private val _isLoadingAfterSending = MutableStateFlow(false)
  val isLoadingAfterSending = _isLoadingAfterSending.asStateFlow()

  private val _selectedMessage = MutableStateFlow(MessageEntity())
  val selectedMessage = _selectedMessage.asStateFlow()

  // Метод для получения помощи от ИИ
  fun askAssistant(userText: String) {
    if (userText.isBlank()) return

    _isLoadingAfterSending.value = true
    viewModelScope.launch {
      try {
        // Call through the repository (Complexity -1)
        val responseText = chatRepo.askAiAssistant(userText)

        _assistantResponse.value = responseText ?: "AI connection error"
        Log.d("Gemini_Debug", "Answer received: $responseText")
      } catch (e: Exception) {
        Log.e("Gemini_Debug", "Error: ${e.message}")
        _assistantResponse.value = "AI connection error"
      } finally {
        _isLoadingAfterSending.value = false
      }
    }
  }



  private fun analyzeIncomingMessage(message: MessageEntity, role: UserRole) {
    // Анализируем только если текущий пользователь — Диспетчер (OsbbUser)
    // и сообщение пришло от жильца
//    if (role == UserRole.OsbbUser && message.senderUid != currentUserId) {
    if (role == UserRole.OsbbUser) {
      viewModelScope.launch {
        try {
          val analysisPrompt = """
                    Проанализируй сообщение жильца: "${message.text}". 
                    1. Определи категорию (Авария, Жалоба, Вопрос).
                    2. Предложи краткий вариант ответа для диспетчера.
                """.trimIndent()

          val result = generativeModel.generateContent(analysisPrompt)
          _quickHint.value = result.text
        } catch (e: Exception) {
          Log.e("Gemini", "Analysis failed")
        }
      }
    }
  }


  fun analyzePhotoWithGemini(uri: Uri, context: android.content.Context) {
    _isLoadingAfterSending.value = true
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Используем уже созданный метод сжатия для экономии ресурсов
        val imageData = compressImage(context, uri)
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)

        val inputContent = content {
          image(bitmap)
          text(
            """
                    Ты — автоматический распознаватель счетчиков. 
                    На этой фотографии изображен счетчик воды. 
                    Найди на нем  № (цифры)  и только черные на табло цифры показаний . 
                    Выведи ТОЛЬКО Счетчик № [число]. и через пробел  Показания: [число].
                """.trimIndent()
          )

        }

        val response = generativeModel.generateContent(inputContent)

        withContext(Dispatchers.Main) {
          val generatedText = response.text ?: ""

          // ПРОВЕРКА: Если пользователь уже что-то ввел, не перезаписываем,
          // а просто сохраняем в assistantResponse (появится как подсказка сверху)
          if (_messageText.value.isBlank()) {
            _messageText.value = generatedText
          } else {
            _assistantResponse.value = generatedText
          }

          _isLoadingAfterSending.value = false
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          _isLoadingAfterSending.value = false
          Log.e("Gemini_Photo", "Ошибка анализа: ${e.message}")
        }
      }
    }
  }


  fun applyAiHint() {
    // Берем текст из любого активного источника (ассистент или быстрый совет)
    val textToApply = assistantResponse.value ?: quickHint.value

    if (!textToApply.isNullOrBlank()) {
      _messageText.value = textToApply
      // Очищаем подсказки после использования
      clearAiSuggestion()
    }
  }


  // Метод для очистки подсказок (вызывается при клике на крестик или после отправки)
  fun clearAiSuggestion() {
    _assistantResponse.value = null
    _quickHint.value = null
  }

  fun writeToDatabase(
    chatUid: String,
    senderUid: String,
    senderDisplayedName: String,
    senderLogoUrl: String?,
    senderAddress: String,
    imageUrl: String?,
    osbbId: Int,
    role: UserRole,
    onComplete: () -> Unit,
    recipientTokens: List<String>
  ) {
    _isLoadingAfterSending.value = true

    // 1. Формируем ID чата (логика прежняя)
    val chatId = when {
      role == UserRole.StandardUser && selectedService.value.codeName == UserRole.OsbbUser.codeName -> {
        "${selectedService.value.codeName}_${osbbId}_$chatUid"
      }

      role == UserRole.StandardUser -> "${selectedService.value.codeName}_$chatUid"
      role == UserRole.OsbbUser -> "${role.codeName}_${osbbId}_$chatUid"
      else -> "${role.codeName}_$chatUid"
    }

    // 2. Используем внедренное свойство chatsReference вместо getInstance()
    val chatRef = chatsReference.child(chatId)
    val key = chatRef.push().key ?: return // Безопасная проверка ключа

    val messageEntity = MessageEntity(
      id = key,
      senderUid = senderUid,
      text = messageText.value,
      senderLogoUrl = senderLogoUrl,
      senderDisplayedName = senderDisplayedName,
      senderAddress = senderAddress,
      imageUrl = imageUrl,
      timestamp = System.currentTimeMillis() // Используем время системы или ServerValue.TIMESTAMP
    )

    // 3. Запись данных
    chatRef.child(key).setValue(messageEntity)
      .addOnCompleteListener { task ->
        _isLoadingAfterSending.value = false
        if (task.isSuccessful) {
          sendPushNotification(
            SendNotificationArguments(
              recipientTokens = recipientTokens,
              title = senderDisplayedName,
              body = messageText.value
            )
          )
          _messageText.value = ""
          onComplete()
        }
      }
      .addOnFailureListener {
        _isLoadingAfterSending.value = false
        SnackbarManager.showMessage(it.message.toString())
      }
  }

  fun readFromDatabase(role: UserRole, senderUid: String, osbbId: Int) {
    val chatId = when {
      role == UserRole.StandardUser && selectedService.value.codeName == UserRole.OsbbUser.codeName -> {
        "${selectedService.value.codeName}_${osbbId}_$senderUid"
      }

      role == UserRole.StandardUser -> "${selectedService.value.codeName}_$senderUid"
      role == UserRole.OsbbUser -> "${role.codeName}_${osbbId}_$senderUid"
      else -> "${role.codeName}_$senderUid"
    }

    val ref = chatsReference.child(chatId)

    // Удаляем старый слушатель для этого пути, если он был (перестраховка)
    listeners[ref]?.let { ref.removeEventListener(it) }

    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        viewModelScope.launch(Dispatchers.Default) {
          val messageList = dataSnapshot.children.mapNotNull {
            it.getValue(MessageEntity::class.java)
          }
          if (messageList.isNotEmpty()) {
            val lastMessage = messageList.last()
            // Вызываем анализ для последнего сообщения
            analyzeIncomingMessage(lastMessage, role)
          }

          withContext(Dispatchers.Main) {
            _firebaseTest.value = messageList
          }
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.w("firebase_error", "Failed to read value.", error.toException())
        SnackbarManager.showMessage(error.message)
      }
    }

    ref.addValueEventListener(listener)
    listeners[ref] = listener
  }

  fun onMessageTextChanged(value: String) {
    _messageText.value = value
  }

  fun trackUserIdentifiersWithRole(role: UserRole, osbbRoleId: Int?) {
    val ref = chatsReference

    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        viewModelScope.launch(Dispatchers.Default) {
          val userIdentifiers = dataSnapshot.children.mapNotNull { chatSnap ->
            val chatId = chatSnap.key ?: return@mapNotNull null
            val condition = if (osbbRoleId != null) {
              chatId.startsWith("${role.codeName}_${osbbRoleId}")
            } else {
              chatId.startsWith(role.codeName)
            }

            if (condition) {
              if (osbbRoleId != null) chatId.substringAfter("${osbbRoleId}_")
              else chatId.substringAfter("_")
            } else null
          }
          withContext(Dispatchers.Main) {
            _userIdentifiersWithRole.value = userIdentifiers
            getUsers()
          }
        }
      }

      override fun onCancelled(error: DatabaseError) { /* Log error */
      }
    }

    ref.addValueEventListener(listener)
    listeners[ref] = listener
  }


    fun getUsers() {
      val userIdentifiers = _userIdentifiersWithRole.value
      if (userIdentifiers.isEmpty()) {
        _userList.value = emptyList()
        return
      }

      viewModelScope.launch {
        try {
          // Используем репозиторий вместо прямой работы с db
          val users = chatRepo.fetchUsersByIds(userIdentifiers)
          _userList.value = users
          Log.d("filtered_test", "Fetched ${users.size} users")
        } catch (e: Exception) {
          SnackbarManager.showMessage("Ошибка загрузки пользователей")
        }
      }
    }



    fun setSelectedUser(user: UserEntity) {
    _selectedUser.value = user
  }

  fun setSelectedService(totalServiceDebt: TotalServiceDebt) {
    val codeName = when (totalServiceDebt.contentDetail) {
      ContentDetail.WARM_SERVICE -> UserRole.YtkeUser.codeName
      ContentDetail.WATER_SERVICE -> UserRole.VodokanalUser.codeName
      ContentDetail.OSBB -> UserRole.OsbbUser.codeName
      else -> UserRole.TboUser.codeName
    }

    // Обновляем стейт атомарно
    _selectedService.value = ServiceWithCodeName(
      name = totalServiceDebt.name,
      codeName = codeName
    )
  }


    fun addChatListener(chatUid: String, onLastMessageChange: (MessageEntity) -> Unit) {
      viewModelScope.launch {
        // Репозиторий скрывает сложность Firebase (Complexity -1)
        chatRepo.observeLastMessage(chatUid)
          .flowOn(Dispatchers.Default) // Маппинг в фоне
          .collect { latestMessage ->
            val message = latestMessage ?: MessageEntity()
            Log.d("chat_listener", "Latest message in $chatUid: ${message.text}")
            onLastMessageChange(message)
          }
      }
    }

    fun uploadPhotoAndSendMessage(
      context: android.content.Context,
      chatUid: String,
      senderUid: String,
      senderDisplayedName: String,
      senderLogoUrl: String?,
      senderAddress: String,
      osbbId: Int,
      role: UserRole,
      onComplete: () -> Unit,
      recipientTokens: List<String>
    ) {
      viewModelScope.launch { // Dispatchers.IO теперь внутри репозитория или через flowOn
        _isLoadingAfterSending.value = true
        try {
          val uri = _selectedImageUri.value
          if (uri == Uri.EMPTY) return@launch

          // --- ШАГ 1: СЖАТИЕ (выполняем в фоне) ---
          val imageData = withContext(Dispatchers.IO) { compressImage(context, uri) }

          // --- ШАГ 2: ЛОГИКА ИДЕНТИФИКАТОРА ---
          val chatId = when {
            role == UserRole.StandardUser && selectedService.value.codeName == UserRole.OsbbUser.codeName -> {
              "${selectedService.value.codeName}_${osbbId}_$chatUid"
            }
            role == UserRole.StandardUser -> "${selectedService.value.codeName}_$chatUid"
            role == UserRole.OsbbUser -> "${role.codeName}_${osbbId}_$chatUid"
            else -> "${role.codeName}_$chatUid"
          }

          // --- ШАГ 3: ЗАГРУЗКА ЧЕРЕЗ РЕПОЗИТОРИЙ ---
          val imageUrl = withContext(Dispatchers.IO) {
            chatRepo.uploadChatImage(imageData, chatId)
          }

          // --- ШАГ 4: ЗАПИСЬ В БД (Main Thread) ---
          writeToDatabase(
            chatUid = chatUid,
            senderUid = senderUid,
            senderDisplayedName = senderDisplayedName,
            senderLogoUrl = senderLogoUrl,
            senderAddress = senderAddress,
            imageUrl = imageUrl,
            osbbId = osbbId,
            role = role,
            recipientTokens = recipientTokens,
            onComplete = {
              _selectedImageUri.value = Uri.EMPTY
              _messageText.value = ""
              clearAiSuggestion()
              _isLoadingAfterSending.value = false
              onComplete()
            }
          )
        } catch (e: Exception) {
          _isLoadingAfterSending.value = false
          Log.e("photo_upload", "Error: ${e.message}")
          SnackbarManager.showMessage("Ошибка: ${e.localizedMessage}")
        }
      }
    }



  private suspend fun compressImage(context: android.content.Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
      val inputStream = context.contentResolver.openInputStream(uri)
      val originalBitmap = BitmapFactory.decodeStream(inputStream)

      // Вычисляем масштаб, чтобы большая сторона была не более 1200px
      val maxSize = 1200f
      val scale = (maxSize / maxOf(originalBitmap.width, originalBitmap.height)).coerceAtMost(1f)

      val width = (originalBitmap.width * scale).toInt()
      val height = (originalBitmap.height * scale).toInt()

      val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
      val outputStream = java.io.ByteArrayOutputStream()

      // Сжимаем в JPEG (80% качества — баланс веса и детализации)
      scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
      outputStream.toByteArray()
    }


  fun setSelectedImageUri(uri: Uri) {
    _selectedImageUri.value = uri
  }
// ... (предыдущий код без изменений)

  // ДОБАВИТЬ ЭТОТ МЕТОД
  fun deleteMessageFromDatabase(
    senderUid: String,
    messageId: String,
    role: UserRole,
    osbbId: Int
  ) {
    // 1. Формируем тот же ID чата, что и при записи/чтении
    val chatId = when {
      role == UserRole.StandardUser && selectedService.value.codeName == UserRole.OsbbUser.codeName -> {
        "${selectedService.value.codeName}_${osbbId}_$senderUid"
      }

      role == UserRole.StandardUser -> "${selectedService.value.codeName}_$senderUid"
      role == UserRole.OsbbUser -> "${role.codeName}_${osbbId}_$senderUid"
      else -> "${role.codeName}_$senderUid"
    }

    // 2. Ссылаемся на конкретный узел сообщения и удаляем его
    chatsReference.child(chatId).child(messageId).removeValue()
      .addOnSuccessListener {
        // Опционально: показать уведомление об успехе
        Log.d("firebase_delete", "Message deleted successfully")
      }
      .addOnFailureListener {
        SnackbarManager.showMessage("Помилка видалення: ${it.message}")
      }
  }


  fun setSelectedMessage(message: MessageEntity) {
    _selectedMessage.value = message
  }
    fun sendPushNotification(sendNotificationArguments: SendNotificationArguments) {
      viewModelScope.launch(Dispatchers.IO) {
        sendNotificationArguments.recipientTokens.forEach { token ->
          try {
            // Вызываем через репозиторий (Complexity -1)
            chatRepo.sendPushNotification(
              token = token,
              title = sendNotificationArguments.title,
              body = sendNotificationArguments.body
            )
          } catch (e: Exception) {
            // Ошибка уже залогирована в репозитории, здесь можно обработать UI если нужно
          }
        }
      }
    }


  override fun onCleared() {
    super.onCleared()
    // Критически важно для Kotzilla: предотвращает утечки памяти и фоновую активность
    listeners.forEach { (ref, listener) ->
      ref.removeEventListener(listener)
    }
    listeners.clear()
  }


}
