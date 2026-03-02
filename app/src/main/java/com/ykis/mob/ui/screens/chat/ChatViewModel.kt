package com.ykis.mob.ui.screens.chat

//import com.google.auth.oauth2.GoogleCredentials
import android.R.attr.text
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.squareup.moshi.Json
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import android.graphics.Bitmap
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content

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
  private val db: FirebaseFirestore,
  private val realtimeDb: FirebaseDatabase,
  private val storage: FirebaseStorage,
  private val functions: FirebaseFunctions,
  private val generativeModel: GenerativeModel,
  logService: LogService
) : BaseViewModel(logService) {

  // Инициализация модели Gemini 1.5 Flash (быстрая и дешевая)
//  private val generativeModel by lazy {
//    Firebase.ai.generativeModel("gemini-2.0-flash-8b")
//  }
//  val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
//    .generativeModel("gemini-3-flash-preview")
  //  }
  // Состояние для ответа ИИ (чтобы показать в UI, например, подсказку)
  // Для жильца: ответ на конкретный вопрос
  private val _assistantResponse = MutableStateFlow<String?>(null)
  val assistantResponse = _assistantResponse.asStateFlow()

  // Для диспетчера: быстрая подсказка на основе входящего сообщения
  private val _quickHint = MutableStateFlow<String?>(null)
  val quickHint = _quickHint.asStateFlow()

  private val chatsReference = realtimeDb.getReference("chats")
  private val storageReference = storage.reference
  private val userDatabase = db
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
    Log.d("Gemini_Debug", "Метод вызван с текстом: $userText") // ДОБАВЬ ЭТО
    if (userText.isBlank()) return
    _isLoadingAfterSending.value = true

    viewModelScope.launch {
      try {
        val prompt = "Ты помощник жильца ОСББ. Ответь на вопрос: $userText"
        val response = generativeModel.generateContent(prompt)
        Log.d("Gemini_Debug", "Ответ получен: ${response.text}") // И ЭТО
        _assistantResponse.value = response.text
      } catch (e: Exception) {
        Log.e("Gemini_Debug", "Ошибка: ${e.message}")
        _assistantResponse.value = "Ошибка связи с ИИ"
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
          text("Ты помощник жильца ОСББ. Кратко (1-2 предложения) опиши проблему на фото для заявки диспетчеру.")
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

    // Ограничиваем список (Firestore позволяет до 30 ID в одном запросе whereIn)
    val idsToFetch = userIdentifiers.distinct().take(30)

    // Используем внедренный userDatabase
    userDatabase.collection("users")
      .whereIn(com.google.firebase.firestore.FieldPath.documentId(), idsToFetch)
      .get()
      .addOnSuccessListener { result ->
        // Мапим результат (уже отфильтрованный сервером)
        val filteredUsers = result.documents.mapNotNull { document ->
          mapToUserEntity(document.id, document.data ?: emptyMap())
        }

        Log.d("filtered_test", "Fetched ${filteredUsers.size} users from Firestore")
        _userList.value = filteredUsers
      }
      .addOnFailureListener { exception ->
        Log.w("user_store_test", "Error getting documents.", exception)
        SnackbarManager.showMessage("Ошибка загрузки пользователей")
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
//    Log.d("osbb_test", "chatUid111 : $chatUid")

    // 1. Используем внедренную через конструктор realtimeDb (0 мс задержки)
    // 2. Ограничиваем выборку последним сообщением через limitToLast(1)
    val reference = realtimeDb.getReference("chats").child(chatUid).limitToLast(1)

    reference.addValueEventListener(object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        // 3. Выполняем маппинг в фоновом потоке, чтобы не фризить UI
        viewModelScope.launch(Dispatchers.Default) {
          val latestMessage = dataSnapshot.children
            .lastOrNull()
            ?.getValue(MessageEntity::class.java)

          // Возвращаемся в Main для обновления UI
          withContext(Dispatchers.Main) {
            latestMessage?.let {
              Log.d("chat_listener", "Latest message in chat $chatUid: ${it.text}")
            } ?: Log.d("chat_listener", "No messages found in chat $chatUid.")

            onLastMessageChange(latestMessage ?: MessageEntity())
          }
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.w("firebase_error", "Failed to read chat $chatUid", error.toException())
      }
    })
  }


  fun uploadPhotoAndSendMessage(
    context: android.content.Context, // Добавляем контекст для доступа к ContentResolver
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
    viewModelScope.launch(Dispatchers.IO) {
      _isLoadingAfterSending.value = true
      try {
        val uri = selectedImageUri.value
        if (uri == Uri.EMPTY) return@launch

        // --- ШАГ 1: СЖАТИЕ ПЕРЕД ОТПРАВКОЙ ---
        val imageData = compressImage(context, uri)

        // --- ШАГ 2: ФОРМИРОВАНИЕ ПУТИ ---
        val chatId = when {
          role == UserRole.StandardUser && selectedService.value.codeName == UserRole.OsbbUser.codeName -> {
            "${selectedService.value.codeName}_${osbbId}_$chatUid"
          }
          role == UserRole.StandardUser -> "${selectedService.value.codeName}_$chatUid"
          role == UserRole.OsbbUser -> "${role.codeName}_${osbbId}_$chatUid"
          else -> "${role.codeName}_$chatUid"
        }
        val cleanChatId = chatId.replace("/", "_")
        val fileName = "${System.currentTimeMillis()}_image.jpg"

        val photoRef = storage.reference
          .child("chat_images")
          .child(cleanChatId)
          .child(fileName)

        // --- ШАГ 3: ЗАГРУЗКА БАЙТОВ (вместо файла) ---
        photoRef.putBytes(imageData).await()
        val imageUrl = photoRef.downloadUrl.await().toString()

        withContext(Dispatchers.Main) {
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
              onComplete()
            }
          )
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          _isLoadingAfterSending.value = false
          Log.e("photo_upload", "Error: ${e.message}")
          SnackbarManager.showMessage("Ошибка: ${e.localizedMessage}")
        }
      }
    }
  }


  private suspend fun compressImage(context: android.content.Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
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

  fun sendPushNotification(
    sendNotificationArguments: SendNotificationArguments
  ) {
    viewModelScope.launch(Dispatchers.IO) { // Обязательно уходим в IO для сетевых вызовов
      for (token in sendNotificationArguments.recipientTokens) {
        try {
          val urlString =
            "https://sendnotification-ai2rm2uxna-uc.a.run.app?token=${token}&body=${sendNotificationArguments.body}&title=${sendNotificationArguments.title}"

          // Используем внедренный инстанс functions (0 мс задержки на разрешение зависимости)
          functions
            .getHttpsCallableFromUrl(urlString.toHttpUrl().toUrl())
            .call()
            .await() // Используем корутины вместо блокирующего вызова

          Log.d("fcm_tokens_test", "Notification sent to: $token")
        } catch (e: Exception) {
          Log.e("fcm_tokens_test", "Error sending to $token: ${e.message}")
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
