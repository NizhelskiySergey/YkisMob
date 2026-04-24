package com.ykis.mob.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.ai.type.content
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.PropertyName
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.ykis.mob.R
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.domain.UserRole
import com.ykis.mob.firebase.service.impl.ChatRepository
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.text.clear

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
  val fileUrl: String? = null, // Ссылка на PDF, Doc и др.
  val fileName: String? = null, // ДОБАВЬ ЭТО ПОЛЕ
  val timestamp: Long = 0L,
  var read: Boolean = false,
  val edited: Boolean = false,

  // Для списков в Firebase лучше использовать такой формат инициализации
  val deletedFor: List<String>? = null,

  @get:PropertyName("forwarded")
  @set:PropertyName("forwarded")
  var isForwarded: Boolean = false
)



data class UserEntity(
  val uid: String = "",
  val userRole: UserRole = UserRole.StandardUser,
  val photoUrl: String? = "",
  val createdAt: Timestamp? = null,
  val displayName: String? = "",
  val email: String? = "",
  val address: String = "", // ДОБАВИЛИ ПОЛЕ
  val osbbId: Int? = null,
  val addressId: Int = 0,
  val tokens: List<String> = emptyList()
)

// Модель для одной строки в списке админа
data class ChatSession(
  val chatId: String,        // Напр: "OSBB_3_1434_UID"
  val residentUid: String,   // Извлекаем из ключа
  val addressId: Int,        // Извлекаем из ключа
  val lastMessage: MessageEntity = MessageEntity(),
  val userProfile: UserEntity? = null // Подгрузим позже
)

fun mapToUserEntity(uid: String, map: Map<String, Any>): UserEntity {
  return UserEntity(
    uid = uid,
    // Читаем роль: берем строку из мапы и превращаем в Enum
    userRole = UserRole.entries.find { it.name == map["userRole"] as? String }
      ?: UserRole.StandardUser,

    photoUrl = map["photoUrl"] as? String,
    createdAt = map["createdAt"] as? Timestamp,

    // В UserFirebase у тебя поле называется "name", а в Entity "displayName"
    // Проверяем оба ключа на всякий случай
    displayName = (map["name"] as? String) ?: (map["displayName"] as? String)
    ?: (map["email"] as? String),

    email = map["email"] as? String,

    // Числа из Firestore всегда приходят как Long
    osbbId = (map["osbbId"] as? Long)?.toInt(),
    addressId = (map["addressId"] as? Long)?.toInt() ?: 0,

    address = (map["address"] as? String) ?: (map["name"] as? String) ?: "",

    // Сопоставляем fcmTokens из базы с полем tokens в Entity
    tokens = (map["fcmTokens"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
  )
}


class ChatViewModel(
  private val chatRepo: ChatRepository,
  logService: LogService
) : BaseViewModel(logService) { // УДАЛИЛИ KoinComponent


  // Перенаправляем ссылки на репозиторий
  // Ссылки на Realtime Database
  private val chatsReference by lazy { chatRepo.realtime.getReference("chats") }
  private val unreadCountListeners = mutableMapOf<Query, ValueEventListener>()

  // Модель ИИ (Gemini)
  private val generativeModel by lazy { chatRepo.aiModel }

  private val _assistantResponse = MutableStateFlow<String?>(null)

  // Хранилище счетчиков: Key = chatId, Value = количество новых сообщений
  val assistantResponse = _assistantResponse.asStateFlow()

  // Для диспетчера: быстрая подсказка на основе входящего сообщения
  private val _quickHint = MutableStateFlow<String?>(null)
  val quickHint = _quickHint.asStateFlow()

  // Хранилище последних сообщений: Key = chatId, Value = MessageEntity
  private val _lastMessages = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())
  val lastMessages: StateFlow<Map<String, MessageEntity>> = _lastMessages.asStateFlow()
  private var lastTypingState = false

  // Список активных слушателей для последних сообщений, чтобы не плодить дубли
  private val lastMessageListeners = mutableMapOf<String, ValueEventListener>()

  // Коллекция для слушателей сообщений (уже должна быть)
  private val listeners = mutableMapOf<DatabaseReference, ValueEventListener>()

  // ДОБАВЬ ЭТИ ДВЕ СТРОКИ:
  private val typingListeners = mutableMapOf<DatabaseReference, ValueEventListener>()

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
  private var currentChatPath: String? = null // Храним путь текущего открытого чата

  // В ChatViewModel.kt
  private val typingReference = FirebaseDatabase.getInstance().getReference("typing")


  // ChatViewModel.kt

  // Хранилище счетчиков: Key = chatId, Value = количество новых сообщений
  private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
  val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()
  private val _isPartnerTyping = MutableStateFlow(false)
  val isPartnerTyping = _isPartnerTyping.asStateFlow()
  private val _recipientTokens = MutableStateFlow<List<String>>(emptyList())
  val recipientTokens = _recipientTokens.asStateFlow()
  private val _messageToDelete = MutableStateFlow<MessageEntity?>(null)
  val messageToDelete = _messageToDelete.asStateFlow()

  private val _editingMessage = MutableStateFlow<MessageEntity?>(null)
  val editingMessage = _editingMessage.asStateFlow()

  // В ChatViewModel.kt


  private var typingJob: Job? = null

  // Группируем сообщения по ID квартиры (суммируем все службы для одного адреса)
  fun subscribeToAllMyApartments(uid: String, osbbId: Int, apartments: List<Int>) {
    val methodName = "ChatViewModel.subscribeToAll"
    val allChatKeys = mutableListOf<String>()

    apartments.forEach { addrId ->
      // Добавляем все 4 службы для каждой квартиры
      allChatKeys.add("OSBB_${osbbId}_${addrId}_$uid")
      allChatKeys.add("WATER_SERVICE_${addrId}_$uid")
      allChatKeys.add("WARM_SERVICE_${addrId}_$uid")
      allChatKeys.add("GARBAGE_SERVICE_${addrId}_$uid")
    }

    Log.d("YkisLog", "$methodName: Запуск мониторинга для ${apartments.size} кв. (Всего веток: ${allChatKeys.size})")

    // Твой стандартный метод, который ставит ValueEventListener на каждую ветку
    subscribeToUnreadCount(allChatKeys)
  }

  // В ChatViewModel.kt
  private val _forwardingMessage = MutableStateFlow<MessageEntity?>(null)
  val forwardingMessage = _forwardingMessage.asStateFlow()

  // Флаг для UI: показываем ли мы кнопку "Выбрать" в списке пользователей
  val isForwardingMode =
    _forwardingMessage.map { it != null }.stateIn(viewModelScope, SharingStarted.Lazily, false)

  fun stopAllTrackers() {
    val methodName = "ChatViewModel.stopAllTrackers()"
    Log.d("YkisLog", "$methodName: [CLEANUP] Остановка всех слушателей чата")

    // 1. Снимаем слушатели сообщений
    listeners.forEach { (reference, listener) ->
      reference.removeEventListener(listener)
    }
    listeners.clear()

    // 2. Снимаем слушатели счетчиков (unreadCounts)
    unreadCountListeners.forEach { (reference, listener) ->
      reference.removeEventListener(listener)
    }
    unreadCountListeners.clear()

    // 3. Снимаем слушатели статуса "печатает"
    typingListeners.forEach { (reference, listener) ->
      reference.removeEventListener(listener)
    }
    typingListeners.clear()

    // 4. Очищаем локальные данные, чтобы UI не показывал старье
    _firebaseTest.value = emptyList()
    _unreadCounts.value = emptyMap()
    currentChatPath = null
  }
  private fun getFileName(context: Context, uri: Uri): String {
    var name = "file"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (cursor.moveToFirst()) {
        name = cursor.getString(nameIndex)
      }
    }
    return name
  }

  fun startForwarding(message: MessageEntity) {
    _forwardingMessage.value = message
    Log.d("YkisLog", "ChatViewModel: Режим пересылки сообщения ${message.id}")
  }

  fun confirmForwardToService(
    service: ContentDetail,
    baseState: BaseUIState,
    targetUser: UserEntity? = null // Добавляем целевого юзера для админа
  ) {
    val chatId = if (baseState.userRole == UserRole.StandardUser) {
      // Логика ЖИЛЬЦА (без изменений)
      if (service == ContentDetail.OSBB) {
        "OSBB_${baseState.osbbId}_${baseState.addressId}_${baseState.uid}"
      } else {
        "${service.name}_${baseState.addressId}_${baseState.uid}"
      }
    } else {
      // Логика АДМИНА (берем данные жильца из targetUser)
      val tUser = targetUser ?: return // Если юзер не передан, выходим
      val prefix = if (service == ContentDetail.OSBB) "OSBB_${baseState.osbbId}" else service.name

      "${prefix}_${tUser.addressId}_${tUser.uid}"
    }

    Log.d("YkisLog", "confirmForward: Назначение -> $chatId")
    sendForwardedMessage(chatId)
  }


  fun cancelForwarding() {
    _forwardingMessage.value = null
  }

  // Метод самой пересылки
  /**
   * Шаг 2а: Пересылка конкретному пользователю (используется Админом)
   */
  fun confirmForward(targetUser: UserEntity) {
    val methodName = "ChatViewModel.confirmForward()"

    // Исправляем: принудительно используем логику пути для Жильца,
    // так как мы пишем в его ветку чата
    val targetChatId = getChatPath(
      role = UserRole.StandardUser, // Гарантируем формат пути "Служба_Дом_Кв_Юзер"
      osbbId = targetUser.osbbId ?: 0,
      addressId = targetUser.addressId,
      targetUserUid = targetUser.uid
    )

    Log.d("YkisLog", "$methodName: [TARGET] Path: $targetChatId")
    sendForwardedMessage(targetChatId)
  }


  /**
   * Вспомогательный метод для физической копии сообщения в Firebase
   */
  private fun sendForwardedMessage(targetChatId: String) {
    val methodName = "ChatViewModel.sendForwardedMessage()"
    val messageToForward = _forwardingMessage.value ?: return
    val myUid = chatRepo.currentUid ?: ""

    Log.d(
      "YkisLog",
      "$methodName: [START] Исходное сообщение: ${messageToForward.id}, Есть фото: ${!messageToForward.imageUrl.isNullOrEmpty()}"
    )

    viewModelScope.launch(Dispatchers.IO) {
      // 1. Создаем уникальный ключ в целевой ветке
      val newMsgKey = chatsReference.child(targetChatId).push().key
      if (newMsgKey == null) {
        Log.e("YkisLog", "$methodName: [ERROR] Не удалось сгенерировать ключ для Firebase")
        return@launch
      }

      // 2. Подготовка копии сообщения
      // Явно прописываем imageUrl, чтобы он гарантированно перенесся в новый объект
      val forwardedMsg = messageToForward.copy(
        id = newMsgKey,
        senderUid = myUid,
        timestamp = System.currentTimeMillis(),
        read = false,
        isForwarded = true,
        imageUrl = messageToForward.imageUrl // КРИТИЧЕСКИ ВАЖНО: сохраняем ссылку на фото
      )

      Log.d(
        "YkisLog",
        "$methodName: [PREPARE] Путь: $targetChatId | URL фото: ${forwardedMsg.imageUrl ?: "null"}"
      )

      // 3. Отправка в Realtime Database
      chatsReference.child(targetChatId).child(newMsgKey).setValue(forwardedMsg)
        .addOnSuccessListener {
          Log.d("YkisLog", "$methodName: [SUCCESS] Переслано в $targetChatId. ID: $newMsgKey")

          // Сбрасываем состояние пересылки в Main потоке
          viewModelScope.launch(Dispatchers.Main) {
            cancelForwarding()
            SnackbarManager.showMessage(R.string.success_send_message)
          }
        }
        .addOnFailureListener { e ->
          Log.e("YkisLog", "$methodName: [FAILED] Ошибка записи: ${e.message}")
          viewModelScope.launch(Dispatchers.Main) {
            SnackbarManager.showMessage(R.string.error_send_message)
          }
        }
    }
  }


  fun deleteForMe(messageId: String) {
    val methodName = "ChatViewModel.deleteForMe()"
    val chatId = currentChatPath ?: return
    val myUid = chatRepo.currentUid ?: return

    viewModelScope.launch(Dispatchers.IO) {
      Log.d("YkisLog", "$methodName: [START] Скрытие сообщения $messageId для пользователя $myUid")

      // Путь: chats/chatId/messageId/deletedFor
      val deletedForRef = chatsReference.child(chatId).child(messageId).child("deletedFor")

      // Получаем текущий список, добавляем свой UID и сохраняем обратно
      deletedForRef.get().addOnSuccessListener { snapshot ->
        val currentList =
          snapshot.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()

        if (!currentList.contains(myUid)) {
          currentList.add(myUid)
          deletedForRef.setValue(currentList).addOnSuccessListener {
            Log.d("YkisLog", "$methodName: [SUCCESS] Сообщение $messageId скрыто локально")
          }.addOnFailureListener { e ->
            Log.e("YkisLog", "$methodName: [ERROR] Ошибка записи: ${e.message}")
          }
        } else {
          Log.d("YkisLog", "$methodName: [SKIP] UID уже в списке скрытых")
        }
      }.addOnFailureListener { e ->
        Log.e("YkisLog", "$methodName: [CRITICAL] Ошибка получения данных: ${e.message}")
      }
    }
  }

  fun observeTypingStatus(chatId: String) {
    val methodName = "ChatViewModel.observeTypingStatus()"
    val myUid = chatRepo.currentUid ?: return // Используем return, если нет юзера

    val ref = typingReference.child(chatId)

    // Сброс состояния
    _isPartnerTyping.value = false
    typingJob?.cancel()

    // 1. Очистка старого слушателя именно для ТИПИНГА
    typingListeners[ref]?.let {
      ref.removeEventListener(it)
      Log.d("YkisLog", "$methodName: Удален старый слушатель для $chatId")
    }

    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        val now = System.currentTimeMillis()

        val isTyping = snapshot.children.any { child ->
          val rawValue = child.value
          val timestamp = when (rawValue) {
            is Long -> rawValue
            is Double -> rawValue.toLong()
            is Number -> rawValue.toLong() // Универсальный вариант
            else -> 0L
          }
          // Собеседник печатал в последние 5-7 секунд
          child.key != myUid && (now - timestamp) < 7000
        }

        // Обновляем UI только если статус реально изменился
        if (_isPartnerTyping.value != isTyping) {
          _isPartnerTyping.value = isTyping
          Log.d("YkisLog", "$methodName: [SYNC] Печатает: $isTyping")
        }

        // Автосброс через 3 секунды, если данные перестали приходить
        if (isTyping) {
          typingJob?.cancel()
          typingJob = viewModelScope.launch {
            delay(3500)
            _isPartnerTyping.value = false
          }
        }
      }

      override fun onCancelled(e: DatabaseError) {
        Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      }
    }

    // 2. Регистрация в специальную карту typingListeners
    ref.addValueEventListener(listener)
    typingListeners[ref] = listener
    Log.d("YkisLog", "$methodName: [ACTIVE] Слушаем статус печати для $chatId")
  }


// ChatViewModel.kt

  // 1. Метод отмены редактирования
  fun cancelEditing() {
    _editingMessage.value = null
    _messageText.value = ""
    Log.d("YkisLog", "ChatViewModel: Редактирование отменено")
  }

  // 2. Обновленный метод изменения текста (с отправкой статуса печати)
  fun onMessageTextChanged(newText: String) {
    _messageText.value = newText

    // Если мы НЕ в режиме редактирования, отправляем "печатает"
    // (При редактировании старого сообщения "печатает" обычно не показывают)
    if (_editingMessage.value == null) {
      setTypingStatus(newText.isNotBlank())
    }
  }

  // 3. Метод сохранения исправленного сообщения
  fun updateMessage(newText: String) {
    val msg = _editingMessage.value ?: return
    val chatId = currentChatPath ?: return

    viewModelScope.launch(Dispatchers.IO) {
      val updates = mapOf(
        "text" to newText,
        "edited" to true // Пометка, что сообщение было изменено
      )
      chatsReference.child(chatId).child(msg.id).updateChildren(updates)
        .addOnSuccessListener {
          Log.d("YkisLog", "ChatViewModel: [SUCCESS] Сообщение ${msg.id} обновлено")
          cancelEditing() // Сбрасываем режим правки и чистим поле
        }
        .addOnFailureListener { e ->
          Log.e("YkisLog", "ChatViewModel: [ERROR] Не удалось обновить: ${e.message}")
        }
    }
  }

  fun fetchAndSelectUser(userUid: String, onComplete: () -> Unit) {
    val methodName = "ChatViewModel.fetchAndSelectUser()"
    Log.d("YkisLog", "$methodName: [START] Поиск данных пользователя: $userUid")

    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Запрашиваем документ напрямую из репозитория/Firestore
        val user = chatRepo.getUserByUid(userUid)

        withContext(Dispatchers.Main) {
          if (user != null) {
            _selectedUser.value = user
            Log.d("YkisLog", "$methodName: [SUCCESS] Пользователь найден: ${user.displayName}")
            onComplete()
          } else {
            Log.e("YkisLog", "$methodName: [ERROR] Пользователь с UID $userUid не найден в базе")
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [CRITICAL] Ошибка поиска: ${e.message}")
      }
    }
  }


  fun startEditing(message: MessageEntity) {
    _editingMessage.value = message
    _messageText.value = message.text // Переносим текст в поле ввода
  }

  fun confirmEdit(newText: String) {
    val msg = _editingMessage.value ?: return
    val chatId = currentChatPath ?: return

    chatsReference.child(chatId).child(msg.id).updateChildren(
      mapOf(
        "text" to newText,
        "edited" to true
      )
    )
    _editingMessage.value = null
    _messageText.value = ""
  }


  // Добавляем логику в существующий метод подписки на сообщения
  // В ChatViewModel.kt
  // В ChatViewModel.kt
  fun subscribeToAllResidentCounters(uid: String, osbbId: Int, addressId: Int) {
    val methodName = "ChatViewModel.subscribeToAllResidentCounters()"

    // Формируем список ключей для всех чатов, которые есть у жильца
    val chatKeys = listOf(
      "OSBB_${osbbId}_${addressId}_$uid",
      "WATER_SERVICE_${addressId}_$uid",
      "WARM_SERVICE_${addressId}_$uid",
      "GARBAGE_SERVICE_${addressId}_$uid"
    )

    Log.d("YkisLog", "$methodName: Запуск счетчиков для $uid (Addr: $addressId)")

    // Вызываем наш основной метод подсчета
    subscribeToUnreadCount(chatKeys)
  }

  // Установка статуса (вызывать при наборе текста)

  fun setTypingStatus(isTyping: Boolean) {
    val methodName = "ChatViewModel.setTypingStatus()"
    val chatId = currentChatPath ?: return
    val myUid = chatRepo.currentUid ?: return
    val ref = typingReference.child(chatId).child(myUid)

    if (isTyping) {
      // Мы убрали проверку lastTypingState, чтобы каждое нажатие
      // обновляло метку времени и "будило" слушателя у собеседника.
      val timestamp = System.currentTimeMillis()

      ref.setValue(timestamp).addOnSuccessListener {
        Log.d("YkisLog", "$methodName: [UPDATE] Метка времени $timestamp отправлена в $chatId")
      }.addOnFailureListener { e ->
        Log.e("YkisLog", "$methodName: [ERROR] Ошибка записи: ${e.message}")
      }
    } else {
      // Когда текст стерт полностью — удаляем узел
      ref.removeValue().addOnSuccessListener {
        Log.d("YkisLog", "$methodName: [STOP] Узел typing удален для $myUid")
      }
      // Сбрасываем локальный флаг, чтобы при следующем вводе сработал блок isTyping
      lastTypingState = false
    }
  }

  // Подписка на статус собеседника

  fun initializeChatOnApartmentAdded(uid: String, osbbId: Int, addressId: Int, addressText: String) {
    val methodName = "ChatViewModel.initializeChat"
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val chatPath = "OSBB_${osbbId}_${addressId}_$uid"
        val chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatPath)

        // Проверяем, есть ли там уже сообщения (чтобы не спамить при повторном добавлении)
        val snapshot = chatRef.get().await()
        if (!snapshot.exists()) {
          Log.d("YkisLog", "$methodName: [INIT] Создание ветки чата для новой квартиры: $addressText")

          val welcomeMessage = hashMapOf(
            "senderUid" to "system", // Помечаем как системное
            "text" to "Квартиру за адресою $addressText додано до системи. Тепер ви можете спілкуватися з диспетчером.",
            "timestamp" to ServerValue.TIMESTAMP,
            "read" to false,
            "senderDisplayedName" to "Система",
            "type" to "TEXT"
          )

          chatRef.push().setValue(welcomeMessage).await()
          Log.d("YkisLog", "$methodName: [SUCCESS] Жилец теперь виден админу")
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      }
    }
  }

  fun subscribeToUnreadCount(chatKeys: List<String>) {
    val methodName = "ChatViewModel.subscribeToUnreadCount"
    val myUid = chatRepo.currentUid ?: return

    chatKeys.forEach { chatId ->
      // 1. Проверяем наличие активного слушателя, чтобы не плодить дубликаты
      val chatRef = chatsReference.child(chatId)
      if (unreadCountListeners.containsKey(chatRef)) return@forEach

      Log.d("YkisLog", "$methodName: [WATCH] Регистрация слушателя для: $chatId")

      // 2. Оптимизированный запрос: слушаем только непрочитанные (read == false)
      val query = chatRef.orderByChild("read").equalTo(false)

      val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
          viewModelScope.launch(Dispatchers.Default) {
            // 3. Подсчет входящих непрочитанных
            val count = snapshot.children.mapNotNull {
              it.getValue(MessageEntity::class.java)
            }.count { it.senderUid != myUid }

            withContext(Dispatchers.Main) {
              // 4. Обновляем мапу (важно: записываем даже 0, чтобы убрать бейдж в UI)
              _unreadCounts.update { currentMap ->
                currentMap + (chatId to count)
              }

              // 5. Логируем только реальные изменения или наличие сообщений
              if (count > 0) {
                Log.d("YkisLog", "$methodName: [$chatId] -> непрочитано: $count")
              } else {
                Log.d("YkisLog", "$methodName: [$chatId] -> все прочитано (0)")
              }
            }
          }
        }

        override fun onCancelled(error: DatabaseError) {
          Log.e("YkisLog", "$methodName: [ERROR] $chatId: ${error.message}")
        }
      }

      // 6. Запуск и сохранение в реестр слушателей
      query.addValueEventListener(listener)
      unreadCountListeners[chatRef] = listener
    }
  }


  // Метод для получения помощи от ИИ
  fun askAssistant(prompt: String) {
    val methodName = "ChatViewModel.askAssistant()"
    Log.d("YkisLog", "$methodName: [START] Вопрос: $prompt")

    _isLoadingAfterSending.value = true

    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Используйте текстовую модель (например, gemini-pro)
        val response = generativeModel.generateContent(prompt)
        val responseText = response.text

        withContext(Dispatchers.Main) {
          if (responseText != null) {
            Log.d("YkisLog", "$methodName: [SUCCESS] Ответ получен")
            _assistantResponse.value = responseText
          } else {
            Log.e("YkisLog", "$methodName: [ERROR] Ответ пустой (null)")
            _assistantResponse.value = "AI connection error (Empty response)"
          }
          _isLoadingAfterSending.value = false
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Log.e("YkisLog", "$methodName: [CRITICAL] ${e.message}")
          _assistantResponse.value = "AI connection error: ${e.localizedMessage}"
          _isLoadingAfterSending.value = false
        }
      }
    }
  }


  fun subscribeToLastMessages(chatKeys: List<String>) {
    val methodName = "ChatViewModel.subscribeToLastMessages()"

    chatKeys.forEach { chatId ->
      // Если мы уже слушаем эту ветку — пропускаем
      if (lastMessageListeners.containsKey(chatId)) return@forEach

      val lastMsgRef = chatsReference.child(chatId).orderByKey().limitToLast(1)

      val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
          val message = snapshot.children.firstOrNull()?.getValue(MessageEntity::class.java)
          if (message != null) {
            _lastMessages.update { currentMap ->
              currentMap + (chatId to message)
            }
            Log.d("YkisLog", "$methodName: New msg in $chatId: ${message.text}")
          }
        }

        override fun onCancelled(error: DatabaseError) {}
      }

      lastMsgRef.addValueEventListener(listener)
      lastMessageListeners[chatId] = listener
    }
  }

  fun markMessagesAsRead(chatId: String) {
    val methodName = "ChatViewModel.markMessagesAsRead()"
    val myUid = chatRepo.currentUid ?: ""
    val ref = chatsReference.child(chatId)

    Log.d("YkisLog", "$methodName: [START] Проверка сообщений в $chatId")

    // Берем последние 50, чтобы не качать всю историю
    ref.limitToLast(50).get().addOnSuccessListener { snapshot ->
      if (!snapshot.exists()) {
        Log.d("YkisLog", "$methodName: Ветка чата пуста.")
        return@addOnSuccessListener
      }

      val updates = mutableMapOf<String, Any?>()
      var incomingCount = 0

      snapshot.children.forEach { child ->
        val msg = child.getValue(MessageEntity::class.java)
        val msgKey = child.key

        if (msg != null && msgKey != null) {
          // Логика: если отправитель не я и сообщение помечено как НЕ прочитанное
          if (msg.senderUid != myUid && !msg.read) {
            updates["$msgKey/read"] = true
            incomingCount++
            Log.d(
              "YkisLog",
              "$methodName: Найдено непрочитанное от ${msg.senderDisplayedName} (ID: $msgKey)"
            )
          }
        }
      }

      if (updates.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [PROCESS] Отправка обновлений в базу: $incomingCount шт.")
        ref.updateChildren(updates).addOnSuccessListener {
          Log.d(
            "YkisLog",
            "$methodName: [SUCCESS] Статус 'read' обновлен для $incomingCount сообщений"
          )

          // Локально сбрасываем счетчик в UI сразу после успеха в базе
          _unreadCounts.update { it + (chatId to 0) }
        }.addOnFailureListener { e ->
          Log.e("YkisLog", "$methodName: [ERROR] Ошибка записи: ${e.message}")
        }
      } else {
        Log.d("YkisLog", "$methodName: [SKIP] Все входящие сообщения уже прочитаны")
      }
    }.addOnFailureListener { e ->
      Log.e("YkisLog", "$methodName: [CRITICAL] Ошибка связи с сервером: ${e.message}")
    }
  }

  // ChatViewModel.kt


  // Шаг 1: Показываем диалог
  fun showDeleteConfirmation(message: MessageEntity) {
    Log.d("YkisLog", "ChatViewModel: Запрос на удаление сообщения ${message.id}")
    _messageToDelete.value = message
  }

  // Шаг 2: Закрываем диалог (Отмена)
  fun dismissDeleteDialog() {
    _messageToDelete.value = null
  }

  // Шаг 3: Физическое удаление
  fun confirmDeletion() {
    // 1. Берем сообщение целиком, чтобы получить imageUrl
    val message = _messageToDelete.value
    val chatId = currentChatPath

    if (chatId == null || message == null) {
      Log.e("YkisLog", "ChatViewModel: [ERROR] Недостаточно данных для удаления")
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      // 2. Если в сообщении была фотография — удаляем её из Storage
      if (!message.imageUrl.isNullOrBlank()) {
        Log.d("YkisLog", "ChatViewModel: [STORAGE] Удаление фото: ${message.imageUrl}")
        chatRepo.deleteImageFromStorage(message.imageUrl)
      }

      // 3. Удаляем само сообщение из Realtime Database
      chatsReference.child(chatId).child(message.id).removeValue()
        .addOnSuccessListener {
          Log.d("YkisLog", "ChatViewModel: [SUCCESS] Сообщение ${message.id} удалено")
          _messageToDelete.value = null // Закрываем диалог
        }
        .addOnFailureListener { e ->
          Log.e("YkisLog", "ChatViewModel: [FAILED] Ошибка: ${e.message}")
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
                    2. Предложи краткий вариант ответа для диспетчера .
                """.trimIndent()

          val result = generativeModel.generateContent(analysisPrompt)
          _quickHint.value = result.text
        } catch (e: Exception) {
          Log.e("Gemini", "Analysis failed")
        }
      }
    }
  }


  // Добавь параметр address
  fun analyzePhotoWithGemini(uri: Uri, context: android.content.Context, address: String) {
    val methodName = "ChatViewModel.analyzeAI"
    Log.d("YkisLog", "$methodName: [START] Анализ фото для адреса: $address")
    _isLoadingAfterSending.value = true

    viewModelScope.launch(Dispatchers.IO) {
      try {
        val imageData = compressImage(context, uri)
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)

        val inputContent = content {
          image(bitmap)
          text("""
                    Ты — помощник ЖКХ. На фото счетчик воды по адресу: $address. 
                    Найди его серийный номер и текущие показания (только черные цифры на табло). 
                    Выведи в формате: Адрес: $address. Счетчик № [число]. Показания: [число].
                    Если на фото не счетчик, кратко опиши что видишь.
                """.trimIndent())
        }

        Log.d("YkisLog", "$methodName: [REQUEST] Отправка в Gemini...")
        val response = generativeModel.generateContent(inputContent)
        val generatedText = response.text ?: ""

        withContext(Dispatchers.Main) {
          Log.d("YkisLog", "$methodName: [SUCCESS] Ответ: $generatedText")

          _assistantResponse.value = generatedText

          // Если поле пустое, сразу вставляем текст с адресом
          if (_messageText.value.isBlank()) {
            _messageText.value = generatedText
          }
          _isLoadingAfterSending.value = false
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
          _isLoadingAfterSending.value = false
          SnackbarManager.showMessage("Не вдалося розпізнати дані")
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
  // [СТАБИЛЬНАЯ ВЕРСИЯ]
  fun writeToDatabase(
    chatUid: String,
    senderUid: String,
    senderDisplayedName: String,
    senderLogoUrl: String?,
    senderAddress: String,
    addressId: Int,
    imageUrl: String?,
    fileUrl: String? = null, // Новый параметр
    fileName: String?,
    osbbId: Int,
    role: UserRole,
    onComplete: () -> Unit,
    recipientTokens: List<String>
  ) {
    val methodName = "ChatViewModel.writeToDatabase()"

    // Если всё пусто — отменяем
    if (messageText.value.isBlank() && imageUrl == null && fileUrl == null) {
      Log.d("YkisLog", "$methodName: [CANCEL] Пустое сообщение")
      return
    }

    val chatId = getChatPath(role, osbbId, addressId, if (role != UserRole.StandardUser) chatUid else null)
    currentChatPath = chatId

    val chatRef = chatsReference.child(chatId)
    val messageKey = chatRef.push().key ?: return

    // Если текста нет, но есть файл — ставим заглушку
    val displayText = if (fileUrl != null && messageText.value.isBlank()) "[Файл]" else messageText.value

    val messageEntity = MessageEntity(
      id = messageKey,
      senderUid = senderUid,
      text = displayText,
      senderLogoUrl = senderLogoUrl,
      senderDisplayedName = senderDisplayedName.substringAfter("|").trim(),
      senderAddress = senderAddress,
      imageUrl = imageUrl,
      fileUrl = fileUrl,
      fileName = fileName,
      timestamp = System.currentTimeMillis(),
      read = false
    )

    // Внутри ChatViewModel
    chatRef.child(messageKey).setValue(messageEntity).addOnCompleteListener { task ->
      if (task.isSuccessful) {
        Log.d("YkisLog", "Chat: [SUCCESS] Записано")

        // ПРИНУДИТЕЛЬНО очищаем на главном потоке
        _messageText.value = ""
        _selectedImageUri.value = Uri.EMPTY

        // Очищаем подсказки ИИ, если они были
        clearAiSuggestion()

        _isLoadingAfterSending.value = false
        onComplete()
      } else {
        _isLoadingAfterSending.value = false
      }
    }

  }





  fun closeChat() {
    Log.d("YkisLog", "ChatViewModel: Закрытие чата. Был путь: $currentChatPath")
    currentChatPath = null
  }


  /**
   * Подписывается на изменения в ветке чата в Realtime Database.
   * Фильтрует путь к данным в зависимости от роли (Жилец/Админ) и ID предприятия.
   */
  // Во ViewModel добавь это поле (вне функций)

  // В ChatViewModel.kt

  fun readFromDatabase(role: UserRole, senderUid: String, osbbId: Int, addressId: Int) {
    val methodName = "ChatViewModel.readFromDatabase()"
    // Генерируем путь один раз и используем его везде
    val targetPath =
      getChatPath(role, osbbId, addressId, if (role != UserRole.StandardUser) senderUid else null)
    val myUid = chatRepo.currentUid ?: ""
    if (currentChatPath == targetPath && listeners.isNotEmpty()) {
      Log.d("YkisLog", "$methodName: [SKIP] Мы уже слушаем $targetPath")
      return
    }
    Log.d("YkisLog", "$methodName: [START] Инициализация чата: $targetPath")

    // 1. КРИТИЧЕСКИЙ МОМЕНТ: Устанавливаем путь НЕМЕДЛЕННО
    // Это лечит ошибку [REJECTED] ... (активен null)
    currentChatPath = targetPath

    // 2. ПОЛНАЯ ОЧИСТКА ПРЕДЫДУЩИХ ПОДПИСОК
    listeners.forEach { (reference, listener) ->
      Log.d("YkisLog", "$methodName: [CLEANUP] Удаление слушателя из ветки: ${reference.key}")
      reference.removeEventListener(listener)
    }
    listeners.clear()

    // 3. ПОДГОТОВКА UI
    _firebaseTest.value = emptyList()
    _isPartnerTyping.value = false
    typingJob?.cancel()

    // Запускаем процессы мониторинга
    markMessagesAsRead(targetPath)
    observeTypingStatus(targetPath)

    val ref = chatsReference.child(targetPath)

    // 4. СОЗДАНИЕ СЛУШАТЕЛЯ
    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        val count = dataSnapshot.childrenCount

        // Сравниваем с локальной константой targetPath, а не с изменяемой currentChatPath
        Log.d("YkisLog", "$methodName: [DATA_RECV] Путь: $targetPath | В базе: $count")

        if (targetPath == currentChatPath) {
          viewModelScope.launch(Dispatchers.Default) {
            val messages = dataSnapshot.children.mapNotNull {
              it.getValue(MessageEntity::class.java)
            }.filter { msg ->
              msg.deletedFor == null || !msg.deletedFor.contains(myUid)
            }.sortedBy { it.timestamp }

            withContext(Dispatchers.Main) {
              // .toList() принуждает Compose обновить экран
              _firebaseTest.value = messages.toList()

              Log.d("YkisLog", "$methodName: [UI_READY] Отображено: ${messages.size} сообщ.")

              if (messages.isNotEmpty()) {
                markMessagesAsRead(targetPath)
              }
            }
          }
        } else {
          Log.w(
            "YkisLog",
            "$methodName: [REJECTED] Данные для $targetPath проигнорированы (активен $currentChatPath)"
          )
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("YkisLog", "$methodName: [ERROR] Ошибка Firebase: ${error.message}")
      }
    }

    // 5. РЕГИСТРАЦИЯ
    Log.d("YkisLog", "$methodName: [LISTENER_ACTIVE] Слушаем: chats/$targetPath")
    ref.addValueEventListener(listener)
    listeners[ref] = listener
  }


  fun clearCurrentChatPath() {
    val chatId = currentChatPath // запоминаем текущий
    currentChatPath = null
    _isPartnerTyping.value = false // Сбрасываем флаг для UI

    // Убираем себя из списка печатающих при выходе
    if (chatId != null) {
      setTypingStatus(false)
    }
  }


  fun deleteMessage(messageId: String) {
    val methodName = "ChatViewModel.deleteMessage()"
    val chatId = currentChatPath ?: return // Удаляем только если путь чата определен

    viewModelScope.launch(Dispatchers.IO) {
      try {
        Log.d("YkisLog", "$methodName: [START] Удаление сообщения $messageId в чате $chatId")

        // Удаляем конкретный узел по его ID
        chatsReference.child(chatId).child(messageId).removeValue()
          .addOnSuccessListener {
            Log.d("YkisLog", "$methodName: [SUCCESS] Сообщение удалено")
          }
          .addOnFailureListener { e ->
            Log.e("YkisLog", "$methodName: [ERROR] Ошибка удаления: ${e.message}")
            SnackbarManager.showMessage("Не удалось удалить сообщение")
          }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [CRITICAL] ${e.message}")
      }
    }
  }


  /**
   * Отслеживает список активных чатов (ID пользователей) для конкретной роли админа.
   * Например: админ ОСББ 105 увидит только ветки, начинающиеся на "OSBB_105_".
   */
  fun trackUserIdentifiersWithRole(role: UserRole, osbbId: Int?) {
    val methodName = "ChatViewModel.trackUserIdentifiersWithRole"
    // 1. КРИТИЧЕСКИЙ ФИКС: Очищаем списки ПЕРЕД запуском нового трекера
    // Это мгновенно уберет квартиры старого ОСББ с экрана
    _userIdentifiersWithRole.value = emptyList()
    _userList.value = emptyList()
    _unreadCounts.value = emptyMap()
    Log.d("YkisLog", "$methodName: [CLEAN] Списки старого ОСББ очищены")

    val ref = chatsReference // Ссылка на /chats/

    // 1. Формируем префикс для фильтрации (например, "OSBB_3_")
    val targetPrefix = if (role == UserRole.OsbbUser && osbbId != null) {
      "OSBB_${osbbId}_"
    } else {
      "${role.codeName.name}_"
    }

    Log.d("YkisLog", "$methodName: [START] Префикс: $targetPrefix")

    // 2. Очистка старого слушателя структуры (чтобы не дублировать логику)
    listeners[ref]?.let {
      ref.removeEventListener(it)
      Log.d("YkisLog", "$methodName: [CLEANUP] Удален старый слушатель структуры")
    }

    // 3. Слушатель появления новых чатов
    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        viewModelScope.launch(Dispatchers.Default) {
          val allKeys = dataSnapshot.children.mapNotNull { it.key }
          val chatKeys = allKeys.filter { it.startsWith(targetPrefix) }

          // ПРЕДОХРАНИТЕЛЬ: Если список веток не изменился — выходим
          val currentKeys = _userIdentifiersWithRole.value
          if (chatKeys.size == currentKeys.size && chatKeys.sorted() == currentKeys.sorted()) {
            return@launch
          }

          Log.d("YkisLog", "$methodName: [EVENT] Изменение структуры веток. Найдено: ${chatKeys.size}")

          withContext(Dispatchers.Main) {
            _userIdentifiersWithRole.value = chatKeys

            if (chatKeys.isNotEmpty()) {
              // КРИТИЧЕСКИЙ ФИКС: Подписываем твой проверенный метод
              // на все найденные ключи (включая новые)
              Log.d("YkisLog", "$methodName: [BADGE_SYNC] Запуск subscribeToUnreadCount для ${chatKeys.size} веток")
              subscribeToUnreadCount(chatKeys)

              getUsers()
            } else {
              Log.d("YkisLog", "$methodName: [EMPTY] Нет активных чатов")
              _userList.value = emptyList()
            }
          }
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("YkisLog", "$methodName: [ERROR] Firebase: ${error.message}")
      }
    }

    ref.addValueEventListener(listener)
    listeners[ref] = listener
    Log.d("YkisLog", "$methodName: [READY] Трекер веток запущен")
  }



// В ChatViewModel.kt

  private fun subscribeToAllOsbbCounters(osbbId: Int) {
    val methodName = "ChatViewModel.subscribeToAllOsbbCounters"
    val ref = chatRepo.realtime.getReference("counters").child("OSBB_$osbbId")

    // Удаляем старый слушатель для этой конкретной ссылки, если он был
    listeners[ref]?.let {
      ref.removeEventListener(it)
      Log.d("YkisLog", "$methodName: [CLEANUP] Удален старый слушатель Badge")
    }

    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        val newCounts = mutableMapOf<String, Int>()
        snapshot.children.forEach { chatSnapshot ->
          val addressId = chatSnapshot.key
          val count = chatSnapshot.child("unreadCount").getValue(Int::class.java) ?: 0
          if (addressId != null) {
            newCounts[addressId] = count
          }
        }
        _unreadCounts.value = newCounts
        Log.d("YkisLog", "$methodName: [UPDATE] Badge обновлены")
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("YkisLog", "$methodName: [ERROR] ${error.message}")
      }
    }

    ref.addValueEventListener(listener)
    listeners[ref] = listener // Сохраняем по ссылке ref
  }


  fun getUsers() {
    val methodName = "ChatViewModel.getUsers"
    val chatKeys = _userIdentifiersWithRole.value

    if (chatKeys.isEmpty()) {
      Log.d("YkisLog", "$methodName: [CANCEL] Список ключей пуст. Очистка списка пользователей.")
      _userList.value = emptyList()
      return
    }

    viewModelScope.launch {
      try {
        Log.d("YkisLog", "$methodName: [START] Веток для обработки: ${chatKeys.size}")

        // Извлекаем UID из ключей (последняя часть после "_")
        val uidsToFetch = chatKeys.map { it.substringAfterLast("_") }
          .filter { it.isNotEmpty() }
          .distinct()

        if (uidsToFetch.isEmpty()) {
          Log.w("YkisLog", "$methodName: [SKIP] Не удалось извлечь UID из ключей")
          return@launch
        }

        Log.d("YkisLog", "$methodName: [FETCH] Запрос профилей для ${uidsToFetch.size} UID")

        // 1. Загрузка профилей из репозитория
        val fetchedProfiles = withContext(Dispatchers.IO) {
          chatRepo.fetchUsersByIds(uidsToFetch)
        }

        Log.d("YkisLog", "$methodName: [DATA] Получено из БД: ${fetchedProfiles.size} профилей")

        // 2. Сбор токенов админов (для push-уведомлений)
        val allAdminTokens = fetchedProfiles
          .filter { it.userRole != UserRole.StandardUser }
          .flatMap { it.tokens ?: emptyList() }
          .distinct()

        _recipientTokens.value = allAdminTokens
        Log.d("YkisLog", "$methodName: [TOKENS] Токенов админов собрано: ${allAdminTokens.size}")

        // 3. Сопоставление профилей с ключами чатов (формируем финальный список для UI)
        val finalUserList = withContext(Dispatchers.Default) {
          chatKeys.mapNotNull { key ->
            val parts = key.split("_")
            if (parts.size < 2) return@mapNotNull null

            val uidFromKey = parts.last()
            // Пытаемся взять адрес из предпоследней части (OSBB_3_1336_UID -> 1336)
            val addrIdFromKey = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0

            fetchedProfiles.find { it.uid == uidFromKey }?.let { profile ->
              profile.copy(addressId = addrIdFromKey)
            }
          }
        }

        // 4. Обновление стейта и запуск слушателей
        withContext(Dispatchers.Main) {
          _userList.value = finalUserList

          Log.d("YkisLog", "$methodName: [SYNC] Запуск подписок на сообщения и бейджи")
          subscribeToUnreadCount(chatKeys)
          subscribeToLastMessages(chatKeys)

          Log.d("YkisLog", "$methodName: [SUCCESS] Список из ${finalUserList.size} пользователей готов")
        }

      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.localizedMessage}")
        // Если ошибка PERMISSION_DENIED — проверьте, чтобы в запросе был фильтр по osbbId
      }
    }
  }


  fun subscribeToResidentCounters(uid: String, osbbId: Int, addressId: Int, addressText: String = "") {
    val methodName = "ChatViewModel.subscribeToResidentCounters()"

    val chatKeys = listOf(
      "OSBB_${osbbId}_${addressId}_$uid",
      "${ContentDetail.WATER_SERVICE.name}_${addressId}_$uid",
      "${ContentDetail.WARM_SERVICE.name}_${addressId}_$uid",
      "${ContentDetail.GARBAGE_SERVICE.name}_${addressId}_$uid"
    )

    Log.d("YkisLog", "$methodName: Мониторинг веток жильца: $chatKeys")

    // --- НОВЫЙ БЛОК: Инициализация ветки чата, чтобы админ увидел жильца ---
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val mainChatPath = chatKeys.first() // Это "OSBB_${osbbId}_${addressId}_$uid"
        val chatRef = chatsReference.child(mainChatPath)

        val snapshot = chatRef.get().await()
        if (!snapshot.exists()) {
          Log.d("YkisLog", "$methodName: [INIT] Создание приветственного сообщения для админа")
          val welcomeMsg = hashMapOf(
            "senderUid" to "system",
            "text" to "Квартиру додано. Чат активовано.",
            "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP,
            "read" to false,
            "senderDisplayedName" to "Система",
            "type" to "TEXT"
          )
          chatRef.push().setValue(welcomeMsg).await()
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [INIT_ERROR] ${e.message}")
      }
    }
    // -----------------------------------------------------------------------

    // 2. ПОДГОТОВКА ТОКЕНОВ ВСЕХ АДМИНОВ
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val admins = chatRepo.fetchAdminsByOsbb(osbbId)
        val adminTokens = admins.flatMap { it.tokens }
        _recipientTokens.value = adminTokens
        Log.d("YkisLog", "$methodName: [TOKENS] Найдено админов: ${admins.size}")
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [ERROR] Не удалось собрать токены", e)
      }
    }

    subscribeToUnreadCount(chatKeys)
  }



  fun setSelectedUser(user: UserEntity) {
    _selectedUser.value = user
  }

  /**
   * Устанавливает текущую выбранную службу для чата на основе данных о долгах.
   * Преобразует категорию контента в строковое имя для формирования ID чата.
   */
  // ChatViewModel.kt
  fun setSelectedService(totalServiceDebt: TotalServiceDebt) {
    val methodName = "ChatViewModel.setSelectedService()"

    // Записываем код службы (напр. "OSBB", "WATER_SERVICE")
    _selectedService.update {
      it.copy(
        name = totalServiceDebt.name,
        codeName = totalServiceDebt.contentDetail.name // Используем .name от Enum
      )
    }

    Log.d(
      "YkisLog",
      "$methodName: Установлена служба ${totalServiceDebt.name}, Code: ${totalServiceDebt.contentDetail.name}"
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
      Log.d(
        "YkisLog",
        "ApartmentViewModel addChatListener() Подписка на последнее сообщение в ветке: $chatUid"
      )

      // 1. Получаем поток (Flow) из репозитория
      chatRepo.observeLastMessage(chatUid)
        // 2. Выполняем маппинг данных (обработку Firebase Snapshot) в фоновом потоке
        .flowOn(Dispatchers.Default)
        // 3. Собираем данные (Collect)
        .collect { latestMessage ->
          // Если в чате еще нет сообщений, создаем пустую сущность
          val message = latestMessage ?: MessageEntity(text = "Нет сообщений")

          Log.d(
            "YkisLog",
            "ApartmentViewModel addChatListener() Обновление чата $chatUid: ${message.text}"
          )

          // 4. Передаем результат обратно в UI через callback
          // Колбэк выполнится в Main потоке, так как viewModelScope.launch по умолчанию в Main
          onLastMessageChange(message)
        }
    }
  }


  /**
   * Комплексный метод: сжимает фото, загружает его в Storage и отправляет сообщение в чат.
   */
  fun uploadFileAndSendMessage(
    context: android.content.Context,
    chatUid: String,
    senderUid: String,
    senderDisplayedName: String,
    senderLogoUrl: String?,
    senderAddress: String,
    addressId: Int,
    osbbId: Int,
    role: UserRole,
    recipientTokens: List<String>,
    onComplete: () -> Unit
  ) {
    viewModelScope.launch {
      val methodName = "ChatViewModel.uploadFile"
      _isLoadingAfterSending.value = true


      try {
        val uri = _selectedImageUri.value
        if (uri == null || uri == Uri.EMPTY) {
          Log.e("YkisLog", "$methodName: [ABORT] Uri пустой")
          _isLoadingAfterSending.value = false
          return@launch
        }

        // 1. ОПРЕДЕЛЯЕМ ТИП ФАЙЛА (Улучшено)
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        val originalFileName = getFileName(context, uri) // Получаем имя в начале
        // Проверка: это картинка по MIME или по расширению/пути (для камеры)
        val isImage = mimeType.startsWith("image") ||
          uri.toString().lowercase().let { it.contains("jpg") || it.contains("jpeg") || it.contains("image") }

        // Исправляем расширение: для фото всегда .jpg, для доков - по системе
        val extension = if (isImage) "jpg"
        else MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "file"

        Log.d("YkisLog", "$methodName: [START] isImage: $isImage, Ext: $extension, Mime: $mimeType")

        // 2. ПОДГОТОВКА ДАННЫХ
        val fileData: ByteArray = if (isImage) {
          Log.d("YkisLog", "$methodName: [PROCESS] Сжатие изображения...")
          withContext(Dispatchers.IO) { compressImage(context, uri) }
        } else {
          Log.d("YkisLog", "$methodName: [PROCESS] Чтение документа без сжатия...")
          withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
          }
        }

        if (fileData.isEmpty()) throw Exception("Файл пуст или недоступен")

        // 3. ФОРМИРУЕМ ПУТЬ В STORAGE (папка зависит от isImage)
        val folder = if (isImage) "chat_images" else "chat_docs"
        val storagePath = "$folder/$osbbId/$addressId/${System.currentTimeMillis()}.$extension"
        Log.d("YkisLog", "$methodName: [STORAGE_PATH] $storagePath")

        // 4. ЗАГРУЗКА В STORAGE
        val downloadUrl = withContext(Dispatchers.IO) {
          chatRepo.uploadChatImage(fileData, storagePath)
        }
        Log.d("YkisLog", "$methodName: [URL_READY] $downloadUrl")

        // 5. ГЕНЕРАЦИЯ CHAT_ID ДЛЯ DATABASE
        val chatId = if (role == UserRole.StandardUser) {
          val serviceCode = selectedService.value.codeName
          if (serviceCode == "OSBB") "OSBB_${osbbId}_${addressId}_$senderUid"
          else "${serviceCode}_${addressId}_$senderUid"
        } else {
          val prefix = if (role == UserRole.OsbbUser) "OSBB_$osbbId" else role.codeName.name
          "${prefix}_${addressId}_$chatUid"
        }

        // 6. ЗАПИСЬ В БАЗУ (Строго разделяем imageUrl и fileUrl)
        writeToDatabase(
          chatUid = chatUid,
          senderUid = senderUid,
          senderDisplayedName = senderDisplayedName,
          senderLogoUrl = senderLogoUrl,
          senderAddress = senderAddress,
          addressId = addressId,
          imageUrl = if (isImage) downloadUrl else null, // Ссылка только если это фото
          fileUrl = if (!isImage) downloadUrl else null,  // Ссылка только если это ДОКУМЕНТ
          fileName = if (!isImage) originalFileName else null, // ПЕРЕДАЕМ ИМЯ
          osbbId = osbbId,
          role = role,
          recipientTokens = recipientTokens,
          onComplete = {
            Log.d("YkisLog", "$methodName: [FINISH] Успешно отправлено")
            _selectedImageUri.value = Uri.EMPTY
            _messageText.value = ""
            clearAiSuggestion() // Очищаем подсказку ИИ после отправки
            _isLoadingAfterSending.value = false
            onComplete()
          }
        )

      } catch (e: Exception) {
        _isLoadingAfterSending.value = false
        Log.e("YkisLog", "$methodName: [CRITICAL_ERROR] ${e.message}")
        SnackbarManager.showMessage("Помилка завантаження файлу")
      }
    }
  }





  private suspend fun compressImage(context: android.content.Context, uri: Uri): ByteArray =
    withContext(Dispatchers.IO) {
      val inputStream = context.contentResolver.openInputStream(uri)
      val originalBitmap =
        BitmapFactory.decodeStream(inputStream) ?: return@withContext byteArrayOf()

      // 1. Вычисляем масштаб (макс. сторона 1200px)
      val maxSize = 1200f
      val scale = (maxSize / maxOf(originalBitmap.width, originalBitmap.height)).coerceAtMost(1f)

      val width = (originalBitmap.width * scale).toInt()
      val height = (originalBitmap.height * scale).toInt()

      // 2. Создаем уменьшенную копию
      val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)

      // 3. ОСВОБОЖДАЕМ ПАМЯТЬ от оригинала (важно!)
      if (originalBitmap != scaledBitmap) {
        originalBitmap.recycle()
      }

      val outputStream = java.io.ByteArrayOutputStream()

      // 4. Сжимаем (80% — идеальный баланс)
      scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)

      val result = outputStream.toByteArray()

      // 5. Освобождаем память от копии
      scaledBitmap.recycle()

      result
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

  // ChatViewModel.kt
  private fun getChatPath(
    role: UserRole,
    osbbId: Int,
    addressId: Int,
    targetUserUid: String?
  ): String {
    val myUid = chatRepo.currentUid ?: ""
    val residentUid = targetUserUid ?: myUid

    val path = when {
      // 1. ОСББ (Жилец пишет или Админ отвечает)
      role == UserRole.OsbbUser || (role == UserRole.StandardUser && selectedService.value.codeName == "OSBB") -> {
        "OSBB_${osbbId}_${addressId}_$residentUid"
      }
      // 2. СЕРВИСЫ (Водоканал, Теплосеть, ТБО)
      else -> {
        // ВАЖНО: берем имя службы. Для жильца это selectedService.codeName,
        // для админа это роль БЕЗ приставки "User" (напр. VodokanalUser -> Vodokanal)
        val servicePrefix = if (role == UserRole.StandardUser) {
          selectedService.value.codeName
        } else {
          role.name.replace("User", "") // "VodokanalUser" -> "Vodokanal"
        }
        "${servicePrefix}_${addressId}_$residentUid"
      }
    }
    Log.d("YkisLog", "Generated Chat Path: $path (Role: $role, AddrID: $addressId)")
    return path
  }


  fun openChatWithUser(user: UserEntity, currentRole: UserRole, currentOsbbId: Int) {
    val methodName = "ChatViewModel.openChatWithUser()"

    // 1. Устанавливаем собеседника
    _selectedUser.value = user
    _firebaseTest.value = emptyList()

    // 2. Логика определения эффективного OSBB ID
    val effectiveOsbbId = if (currentRole == UserRole.OsbbUser) {
      currentOsbbId
    } else {
      user.osbbId ?: 0
    }

    // 3. Формируем chatId
    val chatId = getChatPath(
      role = currentRole,
      osbbId = effectiveOsbbId,
      addressId = user.addressId,
      targetUserUid = user.uid
    )

    // 4. ТОЧНЫЙ СБРОС: Обнуляем локально и помечаем прочитанными в БД
    _unreadCounts.update { currentMap ->
      currentMap + (chatId to 0)
    }
    markMessagesAsRead(chatId) // Идем в базу и ставим флаг read = true

    // 5. ЛОГИРОВАНИЕ
    Log.d("YkisLog", "--- $methodName START ---")
    Log.d("YkisLog", "Target User: ${user.displayName} (UID: ${user.uid})")
    Log.d("YkisLog", "AddressID: ${user.addressId} | EffectiveOSBB: $effectiveOsbbId")
    Log.d("YkisLog", "Action: Counter reset and database sync for $chatId")

    // 6. Запуск чтения базы
    readFromDatabase(
      role = currentRole,
      senderUid = user.uid,
      osbbId = effectiveOsbbId,
      addressId = user.addressId
    )

    Log.d("YkisLog", "--- $methodName END ---")
  }


  override fun onCleared() {
    val methodName = "ChatViewModel.onCleared()"
    super.onCleared()

    // 1. Отключаем всех слушателей, накопленных в Map
    // Это закроет: чтение сообщений чата, счетчики непрочитанных и тексты последних сообщений
    if (listeners.isNotEmpty()) {
      listeners.forEach { (ref, listener) ->
        ref.removeEventListener(listener)
      }
      Log.d("YkisLog", "$methodName: Отключено слушателей: ${listeners.size}")
      listeners.clear()
    }

    // 2. Дополнительная чистка для lastMessageListeners (если они в отдельной мапе)
    if (lastMessageListeners.isNotEmpty()) {
      lastMessageListeners.forEach { (chatId, listener) ->
        // Если ключ содержит ссылку на ветку, отключаем
        chatsReference.child(chatId.replace("_unread", ""))
          .removeEventListener(listener)
      }
      lastMessageListeners.clear()
      Log.d("YkisLog", "$methodName: Фоновые слушатели (Badge/LastMsg) успешно удалены")
    }

    // 3. Обнуляем пути, чтобы при следующем входе чтение запустилось заново
    currentChatPath = null

    Log.d("YkisLog", "$methodName: ViewModel очищена, утечки памяти предотвращены.")
  }


}
