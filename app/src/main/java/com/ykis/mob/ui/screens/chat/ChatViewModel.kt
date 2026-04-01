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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SendNotificationArguments(
  @SerialName("recipient_token") // Заменили @Json
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
  // Ссылки на Realtime Database
  private val chatsReference by lazy { chatRepo.realtime.getReference("chats") }

  // Модель ИИ (Gemini)
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

  /**
   * Записывает сообщение в Realtime Database и отправляет Push-уведомление.
   */
  fun writeToDatabase(
    chatUid: String,            // ID текущего диалога (обычно UID жильца)
    senderUid: String,          // Кто отправляет (жилец или админ)
    senderDisplayedName: String,
    senderLogoUrl: String?,
    senderAddress: String,      // Адрес (для жильцов)
    imageUrl: String?,          // Ссылка на фото (если есть)
    osbbId: Int,                // ID ОСББ
    role: UserRole,             // Роль отправителя
    onComplete: () -> Unit,
    recipientTokens: List<String>
  ) {
    // Если текста нет и картинки нет — ничего не отправляем
    if (messageText.value.isBlank() && imageUrl == null) return

    _isLoadingAfterSending.value = true

    // 1. ФОРМИРОВАНИЕ ID ЧАТА
    // Эта логика гарантирует, что сообщения Водоканала не попадут в Теплосеть,
    // а сообщения одного ОСББ не увидит другое.
    val chatId = when {
      // 1. Жилец пишет в ОСББ
      // Сравниваем строку из базы (codeName) со строковым именем константы Enum
      role == UserRole.StandardUser && selectedService.value.codeName == ContentDetail.OSBB.name -> {
        "OSBB_${osbbId}_$senderUid"
      }

      // 2. Жилец пишет в одну из 3-х городских служб
      role == UserRole.StandardUser -> {
        "${selectedService.value.codeName}_$senderUid"
      }

      // 3. Пишет админ ОСББ
      role == UserRole.OsbbUser -> {
        "OSBB_${osbbId}_$senderUid"
      }

      // 4. Пишет админ городской службы (Водоканал, Теплосеть или Мусор)
      else -> {
        // Здесь берем codeName из роли (Enum) и превращаем в строку через .name
        "${role.codeName.name}_$senderUid"
      }
    }


    // 2. ПОДГОТОВКА ССЫЛКИ И КЛЮЧА
    val chatRef = chatsReference.child(chatId)
    val messageKey = chatRef.push().key ?: return

    // Создаем объект сообщения
    val messageEntity = MessageEntity(
      id = messageKey,
      senderUid = senderUid,
      text = messageText.value,
      senderLogoUrl = senderLogoUrl,
      senderDisplayedName = senderDisplayedName,
      senderAddress = senderAddress,
      imageUrl = imageUrl,
      timestamp = System.currentTimeMillis() // Время для сортировки
    )

    // 3. ЗАПИСЬ В БАЗУ ДАННЫХ
    chatRef.child(messageKey).setValue(messageEntity)
      .addOnCompleteListener { task ->
        _isLoadingAfterSending.value = false
        if (task.isSuccessful) {
          // Если запись успешна — отправляем PUSH через Firebase Cloud Messaging
          sendPushNotification(
            SendNotificationArguments(
              recipientTokens = recipientTokens,
              title = senderDisplayedName,
              body = messageText.value.ifBlank { "Фотография" }
            )
          )
          _messageText.value = "" // Очищаем поле ввода
          onComplete()            // Закрываем экран или прокручиваем чат
        }
      }
      .addOnFailureListener { e ->
        _isLoadingAfterSending.value = false
        SnackbarManager.showMessage(e.message ?: "Ошибка отправки")
      }
  }


  /**
   * Подписывается на изменения в ветке чата в Realtime Database.
   * Фильтрует путь к данным в зависимости от роли (Жилец/Админ) и ID предприятия.
   */
  fun readFromDatabase(role: UserRole, senderUid: String, osbbId: Int) {
    // 1. ФОРМИРОВАНИЕ ID ЧАТА (Должно строго совпадать с логикой записи)
    val chatId = when {
      // 1. Жилец пишет в ОСББ
      // Сравниваем строку из базы (codeName) со строковым именем константы Enum.
      role == UserRole.StandardUser && selectedService.value.codeName == ContentDetail.OSBB.name -> {
        "OSBB_${osbbId}_$senderUid"
      }

      // 2. Жилец пишет в одну из 3-х городских служб
      role == UserRole.StandardUser -> {
        "${selectedService.value.codeName}_$senderUid"
      }

      // 3. Пишет админ ОСББ
      role == UserRole.OsbbUser -> {
        "OSBB_${osbbId}_$senderUid"
      }

      // 4. Пишет админ городской службы (Водоканал, Теплосеть или Мусор)
      else -> {
        // Здесь берем codeName из роли (Enum) и превращаем в строку через .name
        "${role.codeName.name}_$senderUid"
      }
    }


    val ref = chatsReference.child(chatId)

    // 2. УПРАВЛЕНИЕ СЛУШАТЕЛЯМИ (Предотвращение утечек памяти)
    // Если на этой ветке уже висит слушатель — удаляем его перед созданием нового
    listeners[ref]?.let {
      ref.removeEventListener(it)
    }

    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        // Обработку данных выносим в Default поток, чтобы не нагружать UI
        viewModelScope.launch(Dispatchers.Default) {
          // Парсим все сообщения из снимка базы данных
          val messageList = dataSnapshot.children.mapNotNull {
            it.getValue(MessageEntity::class.java)
          }

          if (messageList.isNotEmpty()) {
            val lastMessage = messageList.last()
            // Вызываем анализ последнего сообщения (например, для уведомлений или ИИ)
            analyzeIncomingMessage(lastMessage, role)
          }

          // Обновляем UI-стейт строго в Main потоке
          withContext(Dispatchers.Main) {
            _firebaseTest.value = messageList
          }
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.w("YkisLog", "ApartmentViewModel onCancelled() Ошибка чтения чата $chatId", error.toException())
        SnackbarManager.showMessage(error.message)
      }
    }

    // Регистрация нового слушателя
    ref.addValueEventListener(listener)
    // Сохраняем ссылку, чтобы иметь возможность отписаться при выходе с экрана
    listeners[ref] = listener
  }


  fun onMessageTextChanged(value: String) {
    _messageText.value = value
  }

  /**
   * Отслеживает список активных чатов (ID пользователей) для конкретной роли админа.
   * Например: админ ОСББ 105 увидит только ветки, начинающиеся на "OSBB_105_".
   */
  fun trackUserIdentifiersWithRole(role: UserRole, osbbId: Int?) {
    val ref = chatsReference

    // Удаляем старый слушатель, если он был, чтобы избежать дублирования данных
    listeners[ref]?.let { ref.removeEventListener(it) }

    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        // Выполняем фильтрацию в фоновом потоке (Default), чтобы не вешать UI
        viewModelScope.launch(Dispatchers.Default) {
          // Определяем префикс, который ищем в названиях веток БД
          val targetPrefix = if (role == UserRole.OsbbUser && osbbId != null) {
            "OSBB_${osbbId}_"
          } else {
            "${role.codeName.name}_"
          }

          // Сканируем все ключи (ID чатов) в корне /chats/
          val userIdentifiers = dataSnapshot.children.mapNotNull { chatSnap ->
            val chatId = chatSnap.key ?: return@mapNotNull null

            // Если ветка начинается с нашего префикса (напр. "WATER_SERVICE_")
            if (chatId.startsWith(targetPrefix)) {
              // Извлекаем UID пользователя (всё, что после префикса)
              chatId.removePrefix(targetPrefix)
            } else {
              null
            }
          }.distinct() // Убираем дубликаты, если они возникли

          // Обновляем список ID в Main потоке и запрашиваем данные этих пользователей
          withContext(Dispatchers.Main) {
            _userIdentifiersWithRole.value = userIdentifiers
            // После получения списка ID (UID), загружаем их профили (имена, фото) из Firestore
            getUsers()
          }
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("YkisLog", "ApartmentViewModel trackUserIdentifiersWithRole() Ошибка отслеживания идентификаторов: ${error.message}")
      }
    }

    // Регистрируем слушатель и сохраняем ссылку для очистки в onCleared()
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

  /**
   * Устанавливает текущую выбранную службу для чата на основе данных о долгах.
   * Преобразует категорию контента в строковое имя для формирования ID чата.
   */
  fun setSelectedService(totalServiceDebt: TotalServiceDebt) {
    // 1. Сопоставляем контентный раздел с ролью/службой
    val selectedContentDetail = when (totalServiceDebt.contentDetail) {
      ContentDetail.WARM_SERVICE -> ContentDetail.WARM_SERVICE
      ContentDetail.WATER_SERVICE -> ContentDetail.WATER_SERVICE
      ContentDetail.OSBB -> ContentDetail.OSBB
      ContentDetail.GARBAGE_SERVICE -> ContentDetail.GARBAGE_SERVICE
      else -> ContentDetail.WATER_SERVICE // Значение по умолчанию
    }

    // 2. Обновляем состояние службы
    // В поле codeName записываем строку (.name), чтобы избежать ошибок сравнения
    _selectedService.value = ServiceWithCodeName(
      name = totalServiceDebt.name,
      codeName = selectedContentDetail.name
    )

    Log.d(
      "YkisLog",
      "ApartmentViewModel setSelectedService() Служба для чата установлена: ${totalServiceDebt.name} (${selectedContentDetail.name})"
    )
  }


  /**
   * Подписывается на обновление последнего сообщения в конкретном чате.
   * Используется для динамического обновления текста сообщения в списке чатов.
   */
  fun addChatListener(
    chatUid: String,
    onLastMessageChange: (MessageEntity) -> Unit
  ) {
    // Используем viewModelScope, чтобы подписка автоматически закрылась при уничтожении ViewModel
    viewModelScope.launch {
      Log.d("YkisLog", "ApartmentViewModel addChatListener() Подписка на последнее сообщение в ветке: $chatUid")

      // 1. Получаем поток (Flow) из репозитория
      chatRepo.observeLastMessage(chatUid)
        // 2. Выполняем маппинг данных (обработку Firebase Snapshot) в фоновом потоке
        .flowOn(Dispatchers.Default)
        // 3. Собираем данные (Collect)
        .collect { latestMessage ->
          // Если в чате еще нет сообщений, создаем пустую сущность
          val message = latestMessage ?: MessageEntity(text = "Нет сообщений")

          Log.d("YkisLog", "ApartmentViewModel addChatListener() Обновление чата $chatUid: ${message.text}")

          // 4. Передаем результат обратно в UI через callback
          // Колбэк выполнится в Main потоке, так как viewModelScope.launch по умолчанию в Main
          onLastMessageChange(message)
        }
    }
  }


  /**
   * Комплексный метод: сжимает фото, загружает его в Storage и отправляет сообщение в чат.
   */
  fun uploadPhotoAndSendMessage(
    context: android.content.Context,
    chatUid: String,            // ID диалога (UID жильца)
    senderUid: String,          // Кто отправляет
    senderDisplayedName: String,
    senderLogoUrl: String?,
    senderAddress: String,      // Адрес для идентификации жильца админом
    osbbId: Int,                // ID ОСББ
    role: UserRole,             // Роль отправителя
    onComplete: () -> Unit,
    recipientTokens: List<String>
  ) {
    viewModelScope.launch {
      _isLoadingAfterSending.value = true
      try {
        val uri = _selectedImageUri.value
        if (uri == Uri.EMPTY) {
          _isLoadingAfterSending.value = false
          return@launch
        }

        // --- ШАГ 1: СЖАТИЕ (в фоновом потоке) ---
        // Уменьшаем размер файла перед отправкой, чтобы сэкономить трафик и место в Storage
        val imageData = withContext(Dispatchers.IO) {
          compressImage(context, uri)
        }

        // --- ШАГ 2: ЛОГИКА ИДЕНТИФИКАТОРА ЧАТА ---
        // Должна быть идентична методам чтения и удаления
        val chatId = when {
          // Жилец пишет в ОСББ
          role == UserRole.StandardUser && selectedService.value.codeName == ContentDetail.OSBB.name -> {
            "OSBB_${osbbId}_$chatUid"
          }
          // Жилец пишет в одну из 3-х городских служб
          role == UserRole.StandardUser -> {
            "${selectedService.value.codeName}_$chatUid"
          }
          // Пишет админ ОСББ
          role == UserRole.OsbbUser -> {
            "OSBB_${osbbId}_$chatUid"
          }
          // Пишет админ городской службы (Водоканал, Теплосеть и т.д.)
          else -> {
            "${role.codeName.name}_$chatUid"
          }
        }

        // --- ШАГ 3: ЗАГРУЗКА В FIREBASE STORAGE ---
        // Выполняем через репозиторий, получаем готовую публичную ссылку на фото
        val imageUrl = withContext(Dispatchers.IO) {
          chatRepo.uploadChatImage(imageData, chatId)
        }

        // --- ШАГ 4: ЗАПИСЬ МЕТАДАННЫХ В REALTIME DATABASE ---
        // Передаем полученную imageUrl в метод записи сообщения
        writeToDatabase(
          chatUid = chatUid,
          senderUid = senderUid,
          senderDisplayedName = senderDisplayedName,
          senderLogoUrl = senderLogoUrl,
          senderAddress = senderAddress,
          imageUrl = imageUrl, // Ссылка на загруженное фото
          osbbId = osbbId,
          role = role,
          recipientTokens = recipientTokens,
          onComplete = {
            // Очистка после успешной отправки
            _selectedImageUri.value = Uri.EMPTY
            _messageText.value = ""
            clearAiSuggestion() // Очищаем подсказки Gemini (если были)
            _isLoadingAfterSending.value = false
            onComplete()
          }
        )
      } catch (e: Exception) {
        _isLoadingAfterSending.value = false
        Log.e(
          "YkisLog",
          "ApartmentViewModel uploadPhotoAndSendMessage() Ошибка загрузки фото: ${e.message}"
        )
        SnackbarManager.showMessage("Ошибка загрузки: ${e.localizedMessage}")
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
  /**
   * Удаляет сообщение из Realtime Database.
   * Использует ту же логику формирования ID чата, что и при чтении/записи.
   */
  fun deleteMessageFromDatabase(
    senderUid: String,  // UID жильца (владельца ветки чата)
    messageId: String,  // Уникальный ID сообщения (key)
    role: UserRole,     // Роль того, кто инициирует удаление
    osbbId: Int         // ID объединения
  ) {
    // 1. ФОРМИРОВАНИЕ ID ЧАТА
    // Используем .name для сравнения String (из сервиса) и Enum (ContentDetail)
    val chatId = when {
      // Жилец в чате своего ОСББ
      role == UserRole.StandardUser && selectedService.value.codeName == ContentDetail.OSBB.name -> {
        "OSBB_${osbbId}_$senderUid"
      }
      // Жилец в чате городской службы (Водоканал, Теплосеть и т.д.)
      role == UserRole.StandardUser -> {
        "${selectedService.value.codeName}_$senderUid"
      }
      // Админ ОСББ
      role == UserRole.OsbbUser -> {
        "OSBB_${osbbId}_$senderUid"
      }
      // Админы городских служб
      else -> {
        "${role.codeName.name}_$senderUid"
      }
    }

    // 2. УДАЛЕНИЕ ДАННЫХ
    // Ссылаемся на путь: chats / {chatId} / {messageId}
    chatsReference.child(chatId).child(messageId).removeValue()
      .addOnSuccessListener {
        // Сообщение удалено из базы.
        // Слушатель (ValueEventListener) автоматически обновит список на экране.
        Log.d(
          "YkisLog",
          "ApartmentViewModel deleteMessageFromDatabase() Сообщение $messageId успешно удалено из чата $chatId"
        )
      }
      .addOnFailureListener { e ->
        // Если возникла ошибка (например, нет прав доступа в Security Rules)
        Log.e(
          "YkisLog",
          "ApartmentViewModel deleteMessageFromDatabase()  Ошибка при удалении сообщения",
          e
        )
        SnackbarManager.showMessage("Помилка видалення: ${e.localizedMessage}")
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
    // Проходим по всем активным слушателям и отключаем их от Firebase
    listeners.forEach { (ref, listener) ->
      ref.removeEventListener(listener)
    }
    listeners.clear()
    Log.d("YkisLog", "ApartmentViewModel onCleared() Все слушатели чата успешно отключены")
  }


}
