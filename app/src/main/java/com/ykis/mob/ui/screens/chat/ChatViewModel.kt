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
import kotlin.collections.forEach
import androidx.core.graphics.scale
import com.ykis.mob.domain.apartment.ApartmentEntity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter

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

@Serializable
data class MessageEntity(
  val id: String = "",
  val senderUid: String = "",
  val senderDisplayedName: String = "",
  val senderLogoUrl: String? = null,
  val senderAddress: String = "",
  val text: String = "",
  val type: String = "TEXT", // ДОБАВЬ ЭТО: Firebase ищет это поле
  val imageUrl: String? = null,
  val fileUrl: String? = null,
  val fileName: String? = null,
  val timestamp: Long = 0L,
  var read: Boolean = false,
  val edited: Boolean = false,

  // Используй пустой список по умолчанию для корректного парсинга
  val deletedFor: List<String> = emptyList(),

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
  val nanim: String = "", // ДОБАВИЛИ ПОЛЕ
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
  private val _userIdentifiersWithRole = MutableStateFlow<List<String>>(emptyList())
  private val _rawFetchedProfiles = MutableStateFlow<List<UserEntity>>(emptyList())
// _lastMessages у тебя уже должен быть объявлен как MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

  private val _userList = MutableStateFlow<List<UserEntity>>(emptyList())
  val userList: StateFlow<List<UserEntity>> = combine(
    _userIdentifiersWithRole, // Ключи веток (4 шт)
    _rawFetchedProfiles,      // Загруженные профили
    _lastMessages             // Тексты сообщений (те самые [UPDATE])
  ) { keys, profiles, lastMsgs ->
    keys.mapNotNull { key ->
      val parts = key.split("_")
      if (parts.size < 4) return@mapNotNull null

      val uidFromKey = parts.last()
      val addrIdFromKey = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0

      val profile = profiles.find { it.uid == uidFromKey }
      val lastMsg = lastMsgs[key]

      // Формируем объект: если сообщения еще нет, пишем "Завантаження...", если есть - текст
      val preview = lastMsg?.text ?: "Немає повідомлень"

      profile?.copy(
        addressId = addrIdFromKey,
        address = preview, // Используем nanim как поле для превью
        displayName = lastMsg?.senderAddress ?: profile.displayName
      )
          ?: UserEntity(
            uid = uidFromKey,
            addressId = addrIdFromKey,
            displayName = "Користувач (о/р $addrIdFromKey)",
            address = preview
          )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


  private val _selectedUser = MutableStateFlow<UserEntity>(UserEntity())
  val selectedUser = _selectedUser.asStateFlow()

  private val _selectedService = MutableStateFlow(ServiceWithCodeName())
  val selectedService = _selectedService.asStateFlow()


  private val _selectedImageUri = MutableStateFlow(Uri.EMPTY)
  val selectedImageUri = _selectedImageUri.asStateFlow()

  private val _isLoadingAfterSending = MutableStateFlow(false)
  val isLoadingAfterSending = _isLoadingAfterSending.asStateFlow()

  private val _selectedMessage = MutableStateFlow(MessageEntity())
  val selectedMessage = _selectedMessage.asStateFlow()
  private var currentChatPath: String? = null // Храним путь текущего открытого чата

  // В ChatViewModel.kt

  private val typingReference = FirebaseDatabase.getInstance().getReference("typing")

  // Ссылки для управления активным трекером списка чатов
  private var activeTrackerQuery: Query? = null
  private var activeTrackerListener: ValueEventListener? = null

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


  // В ChatViewModel.kt
  private val _forwardingMessage = MutableStateFlow<MessageEntity?>(null)
  val forwardingMessage = _forwardingMessage.asStateFlow()

  // Флаг для UI: показываем ли мы кнопку "Выбрать" в списке пользователей
  val isForwardingMode =
    _forwardingMessage.map { it != null }.stateIn(viewModelScope, SharingStarted.Lazily, false)

  fun subscribeToAllMyApartments(uid: String, osbbId: Int, apartments: List<Int>) {
    val methodName = "ChatViewModel.subscribeToAll"

    if (uid.isBlank()) {
      Log.e("YkisLog", "$methodName: [ABORT] UID is empty")
      return
    }

    val allChatKeys = mutableListOf<String>()

    // 1. Формируем ключи согласно нашей УНИФИЦИРОВАННОЙ схеме
    apartments.forEach { addrId ->
      // Ветка ОСББ (динамический ID дома)
      allChatKeys.add("OSBB_${osbbId}_${addrId}_$uid")

      // Ветки городских служб (СТРОГО системные ID: 9999, 9998, 9997)
      // Это гарантирует, что жилец и диспетчер ТБО/Водоканала видят одну и ту же ветку
      allChatKeys.add("WATER_SERVICE_9999_${addrId}_$uid")
      allChatKeys.add("WARM_SERVICE_9998_${addrId}_$uid")
      allChatKeys.add("GARBAGE_SERVICE_9997_${addrId}_$uid")
    }

    Log.d(
      "YkisLog",
      "$methodName: [START] Житель: $uid | Квартир: ${apartments.size} | Веток для мониторинга: ${allChatKeys.size}"
    )

    // Выводим пару ключей для самопроверки в консоль
    if (allChatKeys.isNotEmpty()) {
      Log.d("YkisLog", "$methodName: [SAMPLE] Первый ключ: ${allChatKeys.first()}")
    }

    // 2. Запускаем слушатели непрочитанных сообщений
    // subscribeToUnreadCount внутри уже имеет защиту containsKey, так что дубли не страшны
    if (allChatKeys.isNotEmpty()) {
      subscribeToUnreadCount(allChatKeys)
      Log.d("YkisLog", "$methodName: [SUCCESS] Слушатели бейджей активированы")
    } else {
      Log.w("YkisLog", "$methodName: [SKIP] Список квартир пуст, подписка невозможна")
    }
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
    targetUser: UserEntity? = null
  ) {
    val methodName = "ChatViewModel.confirmForwardToService"

    // 1. ОПРЕДЕЛЯЕМ ПРЕФИКС И СИСТЕМНЫЙ ID (Единый стандарт для всех)
    val (servicePrefix, systemId) = when (service) {
      ContentDetail.OSBB -> "OSBB" to (baseState.osmdId ?: baseState.osbbId ?: 0)
      ContentDetail.WATER_SERVICE -> "WATER_SERVICE" to 9999
      ContentDetail.WARM_SERVICE -> "WARM_SERVICE" to 9998
      ContentDetail.GARBAGE_SERVICE -> "GARBAGE_SERVICE" to 9997
      else -> service.name to 0
    }

    val chatId = if (baseState.userRole == UserRole.StandardUser) {
      // 2. Логика ЖИТЕЛЯ (Пересылка из своего кабинета в службу)
      if (baseState.addressId == 0) {
        Log.e("YkisLog", "$methodName: [ABORT] AddressId жильца равен 0")
        return
      }
      "${servicePrefix}_${systemId}_${baseState.addressId}_${baseState.uid}"
    } else {
      // 3. Логика АДМИНА (Пересылка конкретному жильцу)
      // Пытаемся взять целевого юзера из аргумента или из активного чата
      val tUser = targetUser ?: _selectedUser.value ?: run {
        Log.e("YkisLog", "$methodName: [ERROR] Целевой пользователь (targetUser) не определен")
        return
      }

      val targetAddrId = tUser.addressId ?: 0
      if (targetAddrId == 0) {
        Log.e("YkisLog", "$methodName: [ABORT] У целевого пользователя отсутствует addressId")
        return
      }

      // Если админ пересылает в ОСББ, берем ID дома именно этого жильца
      val finalSysId = if (service == ContentDetail.OSBB) {
        tUser.osbbId ?: systemId
      } else {
        systemId
      }

      "${servicePrefix}_${finalSysId}_${targetAddrId}_${tUser.uid}"
    }

    Log.d("YkisLog", "$methodName: [FORWARD_TARGET] $chatId (Service: $servicePrefix)")

    // 4. ФИЗИЧЕСКАЯ ОТПРАВКА
    // В методе sendForwardedMessage убедись, что вызываешь clearForwardingMode() после успеха
    sendForwardedMessage(chatId)
  }



  fun cancelForwarding() {
    _forwardingMessage.value = null
  }

  fun confirmForward(targetUser: UserEntity) {
    val methodName = "ChatViewModel.confirmForward"
    val state = _uiState.value

    // 1. Определяем, в какую именно службу (или ОСББ) мы пересылаем.
    // Если админ уже находится в режиме конкретной службы (напр. Водоканал),
    // берем её префикс. Если нет — по умолчанию ОСББ.
    val currentRole = state.userRole

    val targetChatId = getChatPath(
      role = currentRole, // Передаем реальную роль админа
      osbbId = targetUser.osbbId ?: state.osbbId,
      addressId = targetUser.addressId,
      targetUserUid = targetUser.uid
    )

    Log.d("YkisLog", "$methodName: [TARGET] Role: $currentRole | Path: $targetChatId")

    // 2. Выполняем физическую пересылку
    viewModelScope.launch(Dispatchers.IO) {
      try {
        sendForwardedMessage(targetChatId)

        // 3. После пересылки сбрасываем режим
        withContext(Dispatchers.Main) {
          cancelForwarding()
          SnackbarManager.showMessage("Повідомлення переслано")
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      }
    }
  }


  /**
   * Вспомогательный метод для физической копии сообщения в Firebase
   */
  private fun sendForwardedMessage(targetChatId: String) {
    val methodName = "ChatViewModel.sendForwardedMessage"
    val messageToForward = _forwardingMessage.value ?: return
    val myUid = chatRepo.currentUid ?: ""

    Log.d(
      "YkisLog",
      "$methodName: [START] Пересылка сообщения ${messageToForward.id} -> $targetChatId"
    )

    viewModelScope.launch(Dispatchers.IO) {
      // 1. Создаем уникальный ключ в целевой ветке
      val newMsgKey = chatsReference.child(targetChatId).push().key
      if (newMsgKey == null) {
        Log.e("YkisLog", "$methodName: [ERROR] Не удалось сгенерировать ключ")
        return@launch
      }

      // 2. Подготовка копии сообщения
      // КРИТИЧНО: Явно копируем type, imageUrl, fileUrl и fileName
      val forwardedMsg = messageToForward.copy(
        id = newMsgKey,
        senderUid = myUid,
        timestamp = System.currentTimeMillis(),
        read = false,
        isForwarded = true,
        // Сохраняем все медиа-данные
        type = messageToForward.type,
        imageUrl = messageToForward.imageUrl,
        fileUrl = messageToForward.fileUrl,
        fileName = messageToForward.fileName,
        senderDisplayedName = _uiState.value.displayName ?: "Користувач"
      )

      Log.d(
        "YkisLog",
        "$methodName: [PREPARE] Тип: ${forwardedMsg.type} | Фото: ${forwardedMsg.imageUrl != null}"
      )

      // 3. Запись в Firebase
      chatsReference.child(targetChatId).child(newMsgKey).setValue(forwardedMsg)
        .addOnSuccessListener {
          Log.d("YkisLog", "$methodName: [SUCCESS] Переслано. Новый ID: $newMsgKey")

          // Сбрасываем состояние пересылки в Main потоке
          viewModelScope.launch(Dispatchers.Main) {
            cancelForwarding()
            SnackbarManager.showMessage(R.string.success_send_message)
          }
        }
        .addOnFailureListener { e ->
          Log.e("YkisLog", "$methodName: [FAILED] ${e.message}")
          viewModelScope.launch(Dispatchers.Main) {
            SnackbarManager.showMessage("Помилка пересилання")
          }
        }
    }
  }


  fun deleteForMe(messageId: String) {
    val methodName = "ChatViewModel.deleteForMe"
    val chatId = currentChatPath // Используем твою переменную пути (убедись, что она не null)
    val myUid = chatRepo.currentUid ?: return

    if (chatId.isNullOrEmpty()) {
      Log.e("YkisLog", "$methodName: [ERROR] Путь чата (chatId) не определен")
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      try {
        Log.d("YkisLog", "$methodName: [START] Скрытие $messageId для $myUid")

        // Ссылка на узел deletedFor конкретного сообщения
        val deletedForRef = chatsReference.child(chatId).child(messageId).child("deletedFor")

        // 1. Получаем текущий список тех, кто скрыл сообщение
        val snapshot = deletedForRef.get().await()
        val currentList =
          snapshot.children.mapNotNull { it.getValue(String::class.java) }.toMutableList()

        // 2. Если нашего UID еще нет в списке — добавляем
        if (!currentList.contains(myUid)) {
          currentList.add(myUid)

          // 3. Сохраняем обновленный список обратно в Firebase
          deletedForRef.setValue(currentList).await()

          Log.d("YkisLog", "$methodName: [SUCCESS] Сообщение скрыто в ветке $chatId")
        } else {
          Log.d("YkisLog", "$methodName: [SKIP] Сообщение уже было скрыто ранее")
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL] Ошибка удаления: ${e.message}")
      }
    }
  }


  fun observeTypingStatus(chatId: String) {
    val methodName = "ChatViewModel.observeTypingStatus"
    val myUid = chatRepo.currentUid ?: return

    // Если chatId пустой, выходим
    if (chatId.isBlank()) {
      Log.e("YkisLog", "$methodName: [ERROR] Пустой chatId")
      return
    }

    val ref = typingReference.child(chatId)

    // Сброс текущего состояния
    _isPartnerTyping.value = false
    typingJob?.cancel()

    // 1. ОЧИСТКА: Удаляем старый слушатель для этой ветки, если он был
    typingListeners[ref]?.let {
      ref.removeEventListener(it)
      typingListeners.remove(ref)
      Log.d("YkisLog", "$methodName: [CLEANUP] Старый слушатель удален для $chatId")
    }

    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        val now = System.currentTimeMillis()

        // Проверяем: печатает ли кто-то, кроме меня, за последние 7 секунд
        val isTyping = snapshot.children.any { child ->
          val timestamp = when (val value = child.value) {
            is Long -> value
            is Number -> value.toLong()
            else -> 0L
          }
          child.key != myUid && (now - timestamp) < 7000
        }

        // Обновляем UI только при реальной смене статуса
        if (_isPartnerTyping.value != isTyping) {
          _isPartnerTyping.value = isTyping
          Log.d("YkisLog", "$methodName: [EVENT] Partner is typing: $isTyping (Chat: $chatId)")
        }

        // Автосброс: если пакеты данных перестали приходить, гасим статус через 3.5 сек
        if (isTyping) {
          typingJob?.cancel()
          typingJob = viewModelScope.launch {
            delay(3500)
            if (_isPartnerTyping.value) {
              _isPartnerTyping.value = false
              Log.d("YkisLog", "$methodName: [TIMEOUT] Статус сброшен")
            }
          }
        }
      }

      override fun onCancelled(e: DatabaseError) {
        Log.e("YkisLog", "$methodName: [CANCELLED] ${e.message}")
      }
    }

    // 2. РЕГИСТРАЦИЯ: Добавляем новый слушатель в карту
    ref.addValueEventListener(listener)
    typingListeners[ref] = listener
    Log.d("YkisLog", "$methodName: [READY] Мониторинг запущен для ветки $chatId")
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
    val oldText = _messageText.value
    _messageText.value = newText

    // Обновляем статус в базе только если мы НЕ редактируем старое сообщение
    // и если изменился сам факт наличия текста (чтобы не спамить в сеть)
    if (_editingMessage.value == null) {
      if (oldText.isBlank() != newText.isBlank()) {
        setTypingStatus(newText.isNotBlank())
      }
    }
  }


  // 3. Метод сохранения исправленного сообщения
  fun updateMessage(newText: String) {
    val methodName = "ChatViewModel.updateMessage"
    val msg = _editingMessage.value ?: return
    val chatId = currentChatPath // Если это String?, Kotlin может ругаться ниже

    // Проверка на null и пустоту пути
    if (chatId.isNullOrEmpty()) {
      Log.e("YkisLog", "$methodName: [ERROR] Путь чата не определен")
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      try {
        Log.d("YkisLog", "$methodName: [START] Обновление сообщения ${msg.id}")

        val updates = mapOf(
          "text" to newText,
          "edited" to true // Пометка "изменено" для UI
        )

        // Выполняем обновление в Firebase
        chatsReference.child(chatId).child(msg.id).updateChildren(updates).await()

        Log.d("YkisLog", "$methodName: [SUCCESS] Текст изменен")

        // Возвращаемся в Main поток для сброса UI
        withContext(Dispatchers.Main) {
          cancelEditing() // Чистит поле и сбрасывает _editingMessage
          SnackbarManager.showMessage("Повідомлення змінено")
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL] ${e.message}")
        withContext(Dispatchers.Main) {
          SnackbarManager.showMessage("Помилка при редагуванні")
        }
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

  private var lastTypingSentTime = 0L // Добавь эту переменную в класс

  fun setTypingStatus(isTyping: Boolean) {
    val methodName = "ChatViewModel.setTypingStatus"

    // 1. Безопасное извлечение chatId и UID
    val chatId = currentChatPath
    val myUid = chatRepo.currentUid ?: return

    if (chatId.isNullOrEmpty()) {
      Log.e("YkisLog", "$methodName: [ERROR] Путь чата не определен")
      return
    }

    val ref = typingReference.child(chatId).child(myUid)
    val now = System.currentTimeMillis()

    if (isTyping) {
      // Обновляем метку времени только если прошло более 2.5 сек с последней отправки
      // Это экономит трафик, но держит статус "активным" для собеседника
      if (now - lastTypingSentTime > 2500) {
        lastTypingSentTime = now
        ref.setValue(now).addOnSuccessListener {
          Log.d("YkisLog", "$methodName: [TYPING] Метка времени обновлена в $chatId")
        }.addOnFailureListener { e ->
          Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
        }
      }
    } else {
      // Когда текст стерт полностью — мгновенно удаляем узел
      lastTypingSentTime = 0L
      ref.removeValue().addOnSuccessListener {
        Log.d("YkisLog", "$methodName: [STOP] Статус печати удален")
      }
    }
  }


  fun subscribeToUnreadCount(chatKeys: List<String>) {
    val methodName = "ChatViewModel.subscribeToUnreadCount"
    val myUid = chatRepo.currentUid ?: return

    chatKeys.forEach { chatId ->
      if (chatId.isBlank()) return@forEach

      // 1. Сначала формируем запрос (Query)
      val chatRef = chatsReference.child(chatId)
      val query = chatRef.orderByChild("read").equalTo(false)

      // 2. Теперь проверяем карту, используя созданный Query как ключ
      if (unreadCountListeners.containsKey(query)) {
        Log.d("YkisLog", "$methodName: [SKIP] Слушатель для этого запроса уже активен: $chatId")
        return@forEach
      }

      Log.d("YkisLog", "$methodName: [WATCH] Регистрация для: $chatId")

      val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
          viewModelScope.launch(Dispatchers.Default) {
            val count = snapshot.children.mapNotNull { child ->
              child.getValue(MessageEntity::class.java)
            }.count { it.senderUid != myUid }

            withContext(Dispatchers.Main) {
              _unreadCounts.update { currentMap ->
                currentMap + (chatId to count)
              }
            }
          }
        }

        override fun onCancelled(error: DatabaseError) {
          Log.e("YkisLog", "$methodName: [ERROR] $chatId: ${error.message}")
        }
      }

      // 3. Запуск и сохранение в карту именно QUERY
      query.addValueEventListener(listener)
      unreadCountListeners[query] = listener
    }
  }


  // Метод для получения помощи от ИИ
  fun askAssistant(prompt: String) {
    val methodName = "ChatViewModel.askAssistant"

    // 1. Проверка на пустой запрос
    if (prompt.isBlank()) return

    _isLoadingAfterSending.value = true
    Log.d("YkisLog", "$methodName: [START] Prompt: $prompt")

    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Формируем расширенный контекст для Gemini
        val roleName = when (_uiState.value.userRole) {
          UserRole.VodokanalUser -> "диспетчера Водоканалу"
          UserRole.OsbbUser -> "голови ОСББ"
          else -> "мешканця квартири"
        }

        val addressInfo = _uiState.value.address ?: ""
        val fullPrompt = """
                Ти помічник у мобільному додатку ЖКГ. 
                Ти відповідаєш від імені $roleName. 
                Контекст квартири: $addressInfo.
                Запит користувача: $prompt
            """.trimIndent()

        // 2. Запрос к модели
        val response = generativeModel.generateContent(fullPrompt)
        val responseText = response.text

        withContext(Dispatchers.Main) {
          if (!responseText.isNullOrBlank()) {
            Log.d("YkisLog", "$methodName: [SUCCESS] Ответ получен")
            _assistantResponse.value = responseText
          } else {
            Log.e("YkisLog", "$methodName: [ERROR] Пустой ответ")
            _assistantResponse.value = "Помилка зв'язку з ШІ (порожня відповідь)"
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [CRITICAL] ${e.message}")
        withContext(Dispatchers.Main) {
          _assistantResponse.value = "Помилка ШІ: ${e.localizedMessage}"
        }
      } finally {
        // 3. Гарантированно выключаем лоадер
        withContext(Dispatchers.Main) {
          _isLoadingAfterSending.value = false
        }
      }
    }
  }


  fun subscribeToLastMessages(chatKeys: List<String>) {
    val methodName = "ChatViewModel.subscribeToLastMessages"

    chatKeys.forEach { chatId ->
      if (chatId.isBlank()) return@forEach

      // 1. Формируем запрос на последнее сообщение
      val lastMsgQuery = chatsReference.child(chatId).orderByKey().limitToLast(1)

      // 2. Проверяем, не слушаем ли мы уже этот конкретный Query
      // (Используем chatId как ключ в мапе для простоты проверки)
      if (lastMessageListeners.containsKey(chatId)) return@forEach

      Log.d("YkisLog", "$methodName: [WATCH] Подписка на последнее сообщение: $chatId")

      val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
          // Извлекаем единственный (последний) элемент из результата
          val message = snapshot.children.firstOrNull()?.getValue(MessageEntity::class.java)

          if (message != null) {
            // Обновляем мапу последних сообщений для отображения в списке UserListScreen
            _lastMessages.update { currentMap ->
              currentMap + (chatId to message)
            }
            Log.d("YkisLog", "$methodName: [UPDATE] $chatId -> ${message.text.take(20)}...")
          }
        }

        override fun onCancelled(error: DatabaseError) {
          Log.e("YkisLog", "$methodName: [ERROR] $chatId: ${error.message}")
        }
      }

      // 3. Регистрация слушателя
      lastMsgQuery.addValueEventListener(listener)
      lastMessageListeners[chatId] = listener
    }
  }


  fun markMessagesAsRead(chatId: String) {
    val methodName = "ChatViewModel.markMessagesAsRead"
    val myUid = chatRepo.currentUid ?: return

    if (chatId.isBlank()) {
      Log.e("YkisLog", "$methodName: [ERROR] Пустой chatId")
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      try {
        Log.d("YkisLog", "$methodName: [START] Проверка ветки $chatId")

        val ref = chatsReference.child(chatId)

        // 1. Получаем последние 50 сообщений одним запросом
        val snapshot = ref.limitToLast(50).get().await()

        if (!snapshot.exists()) {
          Log.d("YkisLog", "$methodName: [SKIP] Ветка пуста")
          return@launch
        }

        val updates = mutableMapOf<String, Any>()
        var incomingCount = 0

        // 2. Ищем сообщения, которые прислали НАМ (senderUid != myUid) и которые еще не прочитаны
        snapshot.children.forEach { child ->
          val msg = child.getValue(MessageEntity::class.java)
          val msgKey = child.key

          if (msg != null && msgKey != null) {
            if (msg.senderUid != myUid && !msg.read) {
              updates["$msgKey/read"] = true
              incomingCount++
            }
          }
        }

        // 3. Если есть что обновлять — пишем в базу
        if (updates.isNotEmpty()) {
          Log.d("YkisLog", "$methodName: [PROCESS] Читаем $incomingCount сообщ.")
          ref.updateChildren(updates).await()

          withContext(Dispatchers.Main) {
            // Мгновенно обнуляем бейдж в UI
            _unreadCounts.update { it + (chatId to 0) }
            Log.d("YkisLog", "$methodName: [SUCCESS] Счетчик обнулен")
          }
        } else {
          Log.d("YkisLog", "$methodName: [SKIP] Новых сообщений нет")
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL] ${e.message}")
      }
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
    val methodName = "ChatViewModel.confirmDeletion"
    val message = _messageToDelete.value
    val chatId = currentChatPath // Убедись, что это String (не null)

    if (chatId.isNullOrEmpty() || message == null) {
      Log.e("YkisLog", "$methodName: [ERROR] Данные отсутствуют. Path: $chatId")
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      try {
        Log.d("YkisLog", "$methodName: [START] Удаление сообщения ID: ${message.id}")

        // 1. Если есть медиа-файлы (фото или документы) — удаляем из Storage
        if (!message.imageUrl.isNullOrBlank()) {
          Log.d("YkisLog", "$methodName: [STORAGE] Удаление фото...")
          chatRepo.deleteImageFromStorage(message.imageUrl)
        }

        if (!message.fileUrl.isNullOrBlank()) {
          Log.d("YkisLog", "$methodName: [STORAGE] Удаление файла...")
          chatRepo.deleteImageFromStorage(message.fileUrl) // Используем тот же метод удаления ссылки
        }

        // 2. Удаляем саму запись из Realtime Database
        chatsReference.child(chatId).child(message.id).removeValue().await()

        Log.d("YkisLog", "$methodName: [SUCCESS] Сообщение полностью удалено")

        // 3. Возвращаемся в Main поток для закрытия UI-диалога
        withContext(Dispatchers.Main) {
          _messageToDelete.value = null
          SnackbarManager.showMessage("Повідомлення видалено")
        }

      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL] Ошибка удаления: ${e.message}")
        withContext(Dispatchers.Main) {
          _messageToDelete.value = null
          SnackbarManager.showMessage("Помилка при видаленні")
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
        // 1. Подготовка изображения
        val imageData = compressImage(context, uri)
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)

        val inputContent = content {
          image(bitmap)
          text(
            """
                    Ти — помічник системи ЖКГ. На фото — лічильник води за адресою: $address. 
                    Твоє завдання:
                    1. Знайти серійний номер лічильника.
                    2. Знайти поточні показання (тільки цілі числа, зазвичай чорні цифри на білому фоні).
                    3. Виведи результат суворо у форматі: 
                       Адреса: $address. Лічильник № [номер]. Показники: [число].
                    
                    Якщо на фото не лічильник або цифри розмиті — напиши: "Не вдалося розпізнати дані, спробуйте зробити фото чіткіше".
                    Відповідай тільки українською мовою.
                    """.trimIndent()
          )
        }

        Log.d("YkisLog", "$methodName: [REQUEST] Відправка в Gemini...")
        val response = generativeModel.generateContent(inputContent)
        val generatedText = response.text

        withContext(Dispatchers.Main) {
          if (!generatedText.isNullOrBlank()) {
            Log.d("YkisLog", "$methodName: [SUCCESS] Відповідь: $generatedText")

            _assistantResponse.value = generatedText

            // Якщо поле введення повідомлення порожнє — автоматично вставляємо результат розпізнавання
            if (_messageText.value.isBlank()) {
              _messageText.value = generatedText
            }
          } else {
            Log.w("YkisLog", "$methodName: [EMPTY] ІІ повернув порожній текст")
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
        withContext(Dispatchers.Main) {
          SnackbarManager.showMessage("Помилка розпізнавання: перевірте підключення")
        }
      } finally {
        // 3. ГАРАНТОВАНО вимикаємо лоадер
        withContext(Dispatchers.Main) {
          _isLoadingAfterSending.value = false
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
    fileUrl: String? = null,
    fileName: String?,
    osbbId: Int,
    role: UserRole,
    onComplete: () -> Unit,
    recipientTokens: List<String>
  ) {
    val methodName = "ChatViewModel.writeToDatabase"

    // 1. Проверка на пустоту
    if (messageText.value.isBlank() && imageUrl == null && fileUrl == null) {
      Log.d("YkisLog", "$methodName: [CANCEL] Пустое сообщение")
      return
    }

    // 2. КРИТИЧЕСКИЙ ФИКС UID: Если мы админ и chatUid пуст, берем UID из выбранного юзера
    val finalTargetUid = if (role != UserRole.StandardUser) {
      chatUid.ifBlank { _selectedUser.value?.uid ?: "" }
    } else {
      null // Житель пишет в общую ветку (его UID подставится внутри getChatPath)
    }

    // 3. ОПРЕДЕЛЯЕМ СИСТЕМНЫЙ ID (для путей организации)
    val effectiveOsbbId = when (role) {
      UserRole.VodokanalUser -> 9999
      UserRole.YtkeUser -> 9998
      UserRole.TboUser -> 9997
      else -> osbbId
    }

    // 4. Генерация УНИФИЦИРОВАННОГО пути
    val chatId = getChatPath(
      role = role,
      osbbId = effectiveOsbbId,
      addressId = addressId,
      targetUserUid = finalTargetUid
    )

    // Обновляем текущий путь
    currentChatPath = chatId

    val chatRef = chatsReference.child(chatId)
    val messageKey = chatRef.push().key ?: return

    Log.d("YkisLog", "$methodName: [START] Path: $chatId | TargetUID: $finalTargetUid")

    val displayText = if (fileUrl != null && messageText.value.isBlank()) "[Файл]" else messageText.value

    // 5. Формируем объект сообщения
    val messageEntity = MessageEntity(
      id = messageKey,
      senderUid = senderUid,
      text = displayText,
      type = if (imageUrl != null) "IMAGE" else if (fileUrl != null) "FILE" else "TEXT",
      senderLogoUrl = senderLogoUrl,
      senderDisplayedName = senderDisplayedName.substringAfter("|").trim(),
      senderAddress = senderAddress,
      imageUrl = imageUrl,
      fileUrl = fileUrl,
      fileName = fileName,
      timestamp = System.currentTimeMillis(),
      read = false
    )

    _isLoadingAfterSending.value = true

    // 6. Запись в Firebase
    chatRef.child(messageKey).setValue(messageEntity).addOnCompleteListener { task ->
      _isLoadingAfterSending.value = false
      if (task.isSuccessful) {
        Log.d("YkisLog", "$methodName: [SUCCESS] Отправлено в $chatId")

        // Очистка
        _messageText.value = ""
        _selectedImageUri.value = Uri.EMPTY
        clearAiSuggestion()
        onComplete()
      } else {
        Log.e("YkisLog", "$methodName: [ERROR] ${task.exception?.message}")
        SnackbarManager.showMessage("Помилка відправки")
      }
    }
  }




  /**
   * Подписывается на изменения в ветке чата в Realtime Database.
   * Фильтрует путь к данным в зависимости от роли (Жилец/Админ) и ID предприятия.
   */
  // Во ViewModel добавь это поле (вне функций)

  // В ChatViewModel.kt

  fun readFromDatabase(role: UserRole, senderUid: String, osbbId: Int, addressId: Int) {
    val methodName = "ChatViewModel.readFromDatabase"

    // 1. УНИФИКАЦИЯ ID
    val effectiveOsbbId = when (role) {
      UserRole.VodokanalUser -> 9999
      UserRole.YtkeUser -> 9998
      UserRole.TboUser -> 9997
      else -> osbbId
    }

    // 2. ГЕНЕРАЦИЯ ПУТИ
    val targetPath = getChatPath(
      role = role,
      osbbId = effectiveOsbbId,
      addressId = addressId,
      targetUserUid = if (role != UserRole.StandardUser) senderUid else null
    )

    currentChatPath = targetPath
    Log.d("YkisLog", "$methodName: [INIT] Путь: $targetPath | Role: $role")

    val myUid = chatRepo.currentUid ?: ""
    val ref = chatsReference.child(targetPath)

    if (listeners.containsKey(ref)) {
      Log.d("YkisLog", "$methodName: [SKIP] Слушатель уже активен")
      return
    }

    // 4. БЕЗОПАСНАЯ ОЧИСТКА СТАРЫХ СЛУШАТЕЛЕЙ
    val iterator = listeners.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      Log.d("YkisLog", "$methodName: [CLEANUP] Удаление слушателя: ${entry.key.key}")
      entry.key.removeEventListener(entry.value)
      iterator.remove()
    }

    _firebaseTest.value = emptyList()
    _isPartnerTyping.value = false
    typingJob?.cancel()

    // 5. ПЕРВИЧНЫЙ СБРОС (при входе)
    markMessagesAsRead(targetPath)
    observeTypingStatus(targetPath)

    // 6. ОСНОВНОЙ СЛУШАТЕЛЬ С ЛОГИКОЙ AUTO-READ
    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        val activePath = currentChatPath
        Log.d("YkisLog", "$methodName: [DATA_RECV] Данные для: $targetPath")

        if (targetPath == activePath) {
          viewModelScope.launch(Dispatchers.Default) {
            val messages = dataSnapshot.children.mapNotNull {
              it.getValue(MessageEntity::class.java)
            }.filter { msg ->
              msg.deletedFor == null || !msg.deletedFor.contains(myUid)
            }.sortedBy { it.timestamp }

            withContext(Dispatchers.Main) {
              _firebaseTest.value = messages.toList()
              Log.d("YkisLog", "$methodName: [UI_READY] Сообщений: ${messages.size}")

              // КРИТИЧЕСКИЙ ФИКС ДЛЯ ГАЛОЧЕК (AUTO-READ)
              if (messages.isNotEmpty()) {
                val lastMsg = messages.last()
                // Если последнее сообщение от собеседника и оно еще не прочитано
                if (lastMsg.senderUid != myUid && !lastMsg.read) {
                  Log.d("YkisLog", "$methodName: [AUTO_READ] Новое сообщение от ${lastMsg.senderUid}. Помечаю прочитанным.")
                  markMessagesAsRead(targetPath)
                } else {
                  Log.d("YkisLog", "$methodName: [READ_STATUS] Последнее сообщение уже прочитано или отправлено мной.")
                }
              }
            }
          }
        } else {
          Log.w("YkisLog", "$methodName: [REJECTED] Ожидали: $activePath, пришло: $targetPath")
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("YkisLog", "$methodName: [ERROR] Firebase: ${error.message}")
      }
    }

    ref.addValueEventListener(listener)
    listeners[ref] = listener
    Log.d("YkisLog", "$methodName: [LISTENER_START] Мониторинг ветки активен")
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


  fun deleteChatThreads(uid: String, osbbId: Int, addressId: Int) {
    val methodName = "ChatViewModel.deleteChatThreads"

    // Те же ключи, что мы создавали при добавлении
    val chatKeys = listOf(
      "OSBB_${osbbId}_${addressId}_$uid",
      "WATER_SERVICE_9999_${addressId}_$uid",
      "WARM_SERVICE_9998_${addressId}_$uid",
      "GARBAGE_SERVICE_9997_${addressId}_$uid"
    )

    Log.d("YkisLog", "$methodName: [START] Удаление чатов для о/р $addressId")

    viewModelScope.launch(Dispatchers.IO) {
      chatKeys.forEach { chatPath ->
        try {
          // Удаляем всю ветку целиком
          chatsReference.child(chatPath).removeValue().await()
          Log.d("YkisLog", "$methodName: [SUCCESS] Ветка $chatPath удалена")
        } catch (e: Exception) {
          Log.e("YkisLog", "$methodName: [ERROR] Не удалось удалить $chatPath: ${e.message}")
        }
      }
    }
  }


  /**
   * Отслеживает список активных чатов (ID пользователей) для конкретной роли админа.
   * Например: админ ОСББ 105 увидит только ветки, начинающиеся на "OSBB_105_".
   */
  fun trackUserIdentifiersWithRole(role: UserRole, osbbId: Int?) {
    val methodName = "ChatViewModel.trackUserIdentifiersWithRole"
    if (role == UserRole.StandardUser) {
      Log.d("YkisLog", "$methodName: [CANCEL] Жильцу не нужен трекер списка")
      return
    }
    // 1. Очистка активных слушателей перед новым поиском
    cleanupTracker()

    // ВАЖНО: Не очищаем _userList здесь принудительно, если ключи совпадут,
    // чтобы избежать "мигания" экрана при каждом клике в Rail.
    _unreadCounts.value = emptyMap()
    Log.d("YkisLog", "$methodName: [CLEAN] Слушатели очищены")

    // 2. ФОРМИРУЕМ ПРЕФИКС
    val targetPrefix = when (role) {
      UserRole.VodokanalUser -> "WATER_SERVICE_9999_"
      UserRole.YtkeUser -> "WARM_SERVICE_9998_"
      UserRole.TboUser -> "GARBAGE_SERVICE_9997_"
      UserRole.OsbbUser -> "OSBB_${osbbId}_"
      else -> "UNKNOWN_"
    }

    Log.d("YkisLog", "$methodName: [START] Поиск веток: $targetPrefix")

    // 3. ОПТИМИЗИРОВАННЫЙ ЗАПРОС
    val query = chatsReference.orderByKey()
      .startAt(targetPrefix)
      .endAt(targetPrefix + "\uf8ff")

    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        val chatKeys = dataSnapshot.children.mapNotNull { it.key }
        Log.d("YkisLog", "$methodName: [EVENT] Найдено веток в диапазоне: ${chatKeys.size}")

        viewModelScope.launch(Dispatchers.Default) {
          // Сравниваем ключи
          val keysIdentical = chatKeys.sorted() == _userIdentifiersWithRole.value.sorted()
          // Проверяем, есть ли уже данные в UI
          val uiPopulated = _userList.value.isNotEmpty()

          // ПРОПУСКАЕМ обновление только если и ключи те же, и список в UI не пуст
          if (keysIdentical && uiPopulated) {
            Log.d("YkisLog", "$methodName: [SKIP] Данные идентичны, UI уже отображается")
            return@launch
          }

          withContext(Dispatchers.Main) {
            _userIdentifiersWithRole.value = chatKeys

            if (chatKeys.isNotEmpty()) {
              Log.d("YkisLog", "$methodName: [PROCEED] Загрузка профилей для ${chatKeys.size} веток")
              subscribeToUnreadCount(chatKeys)
              getUsers() // Этот метод наполнит _userList
            } else {
              _userList.value = emptyList()
              Log.w("YkisLog", "$methodName: [EMPTY] Ветки не найдены в базе")
            }
          }
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("YkisLog", "$methodName: [ERROR] Firebase: ${error.message}")
      }
    }

    // Сохраняем ссылки для очистки
    query.addValueEventListener(listener)
    activeTrackerQuery = query
    activeTrackerListener = listener
    Log.d("YkisLog", "$methodName: [READY] Трекер активен по диапазону $targetPrefix")
  }



// В ChatViewModel.kt

  private fun cleanupTracker() {
    activeTrackerQuery?.let { query ->
      activeTrackerListener?.let { listener ->
        Log.d("YkisLog", "ChatViewModel.cleanupTracker: Удаление старого трекера")
        query.removeEventListener(listener)
      }
    }
    activeTrackerQuery = null
    activeTrackerListener = null
  }

  fun getUsers() {
    val methodName = "ChatViewModel.getUsers"
    val chatKeys = _userIdentifiersWithRole.value

    if (chatKeys.isEmpty()) {
      Log.d("YkisLog", "$methodName: [CANCEL] Список ключей пуст")
      _userList.value = emptyList()
      return
    }

    viewModelScope.launch {
      try {
        Log.d("YkisLog", "--- $methodName [START] ---")
        Log.d("YkisLog", "$methodName: Ключи из Firebase: $chatKeys")

        // 1. Извлечение UID
        val uidsToFetch = chatKeys.map { it.substringAfterLast("_") }
          .filter { it.isNotEmpty() }
          .distinct()
        Log.d("YkisLog", "$methodName: Уникальные UID для загрузки: $uidsToFetch")

        // 2. Загрузка профилей
        val fetchedProfiles = withContext(Dispatchers.IO) {
          val profiles = chatRepo.fetchUsersByIds(uidsToFetch)
          Log.d("YkisLog", "$methodName: Загружено из БД: ${profiles.size} профилей")
          profiles
        }

        // 3. Формирование списка
        val finalUserList = withContext(Dispatchers.Default) {
          chatKeys.mapNotNull { key ->
            val parts = key.split("_")
            if (parts.size < 4) {
              Log.e("YkisLog", "$methodName: [ERR] Ключ не по формату: $key")
              return@mapNotNull null
            }

            val uidFromKey = parts.last()
            val addrIdFromKey = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0

            val profile = fetchedProfiles.find { it.uid == uidFromKey }
            val lastMsg = _lastMessages.value[key] // Пытаемся взять превью из текущего кэша

            Log.d("YkisLog", "$methodName: [PROCESS] Key: $key | Addr: $addrIdFromKey | LastMsgText: ${lastMsg?.text ?: "NULL"}")

            val user = if (profile != null) {
              profile.copy(
                addressId = addrIdFromKey,
                displayName = lastMsg?.senderAddress ?: profile.displayName,
                // СЮДА записываем превью (убедись, что это поле есть в UserEntity)
                   nanim =  lastMsg?.text ?: "Немає повідомлень"
              )
            } else {
              UserEntity(
                uid = uidFromKey,
                addressId = addrIdFromKey,
                displayName = lastMsg?.senderAddress ?: "Користувач (о/р $addrIdFromKey)",
                nanim = lastMsg?.text ?: "Немає повідомлень"
              )
            }
            user
          }
        }

        // 4. Публикация в UI
        withContext(Dispatchers.Main) {
          // Просто обновляем список профилей, combine сам пересоберет userList
          _rawFetchedProfiles.value = fetchedProfiles

          Log.d("YkisLog", "$methodName: [UI_UPDATE] Список из ${finalUserList.size} чел. отправлен в State")

          // 5. Запуск слушателей (именно они потом будут менять _lastMessages)
          Log.d("YkisLog", "$methodName: [SYNC_START] Запуск подписок на бейджи и сообщения...")
          subscribeToUnreadCount(chatKeys)
          subscribeToLastMessages(chatKeys)
        }

      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.stackTraceToString()}")
      }
    }
  }


  fun subscribeToResidentCounters(
    uid: String,
    osbbId: Int,
    addressId: Int,
    addressText: String = ""
  ) {
    val methodName = "ChatViewModel.subscribeToResidentCounters"

    // 1. УНИФИЦИРОВАННЫЕ КЛЮЧИ (Схема: ПРЕФИКС_СИСТЕМНЫЙ_ID_ADDR_UID)
    val chatKeys = listOf(
      "OSBB_${osbbId}_${addressId}_$uid",
      "WATER_SERVICE_9999_${addressId}_$uid",
      "WARM_SERVICE_9998_${addressId}_$uid",
      "GARBAGE_SERVICE_9997_${addressId}_$uid"
    )

    Log.d("YkisLog", "$methodName: [START] Активация 4-х служб для о/р $addressId")

    viewModelScope.launch(Dispatchers.IO) {
      chatKeys.forEach { chatPath ->
        try {
          val chatRef = chatsReference.child(chatPath)
          val snapshot = chatRef.get().await()

          // Инициализируем только пустые ветки
          if (!snapshot.exists() || snapshot.childrenCount == 0L) {
            Log.d("YkisLog", "$methodName: [INIT] Создание узла 'init' в $chatPath")

            val infoText = if (addressText.isNotEmpty()) {
              "Додано: $addressText (о/р $addressId). Чат активовано."
            } else {
              "Квартиру додано (о/р $addressId). Чат активовано."
            }

            // Формируем структуру MessageEntity для Firebase
            val welcomeMsg = hashMapOf(
              "id" to "init",
              "senderUid" to "system", // Системное сообщение
              "senderDisplayedName" to "Система",
              "senderAddress" to addressText,
              "text" to infoText,
              "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP,
              "read" to false,
              "type" to "TEXT",
              "edited" to false,
              "forwarded" to false
            )

            // Фиксированный ключ "init" предотвращает дублирование при повторных вызовах
            chatRef.child("init").setValue(welcomeMsg).await()
          } else {
            Log.d("YkisLog", "$methodName: [SKIP] Ветка $chatPath уже содержит сообщения")
          }
        } catch (e: Exception) {
          Log.e("YkisLog", "$methodName: [ERROR] Путь $chatPath: ${e.message}")
        }
      }
    }

    // 2. СБОР ТОКЕНОВ АДМИНОВ ДЛЯ ПУШЕЙ
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Собираем токены админов данного ОСББ
        val admins = chatRepo.fetchAdminsByOsbb(osbbId)
        val adminTokens = admins.flatMap { it.tokens }.distinct()

        _recipientTokens.value = adminTokens
        Log.d(
          "YkisLog",
          "$methodName: [TOKENS] Найдено админов: ${admins.size}, токенов: ${adminTokens.size}"
        )
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [TOKENS_ERROR] ${e.message}")
      }
    }

    // 3. ПОДПИСКА НА СЧЕТЧИКИ (Чтобы жилец видел бейджи на иконке чата)
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
    val methodName = "ChatViewModel.setSelectedService"

    // 1. Извлекаем системное имя из Enum (ContentDetail)
    val serviceCode = totalServiceDebt.contentDetail.name

    // 2. Обновляем состояние выбранной службы
    _selectedService.update { current ->
      current.copy(
        name = totalServiceDebt.name,
        codeName = serviceCode
      )
    }

    // 3. Логируем для проверки путей
    Log.d(
      "YkisLog",
      "$methodName: Выбрана служба: ${totalServiceDebt.name} | " +
        "Firebase Prefix: $serviceCode"
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

        // 1. ОПРЕДЕЛЯЕМ ТИП ФАЙЛА
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        val originalFileName = getFileName(context, uri)

        val isImage = mimeType.startsWith("image") ||
          uri.toString().lowercase()
            .let { it.contains("jpg") || it.contains("jpeg") || it.contains("image") }

        val extension = if (isImage) "jpg"
        else MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "file"

        Log.d("YkisLog", "$methodName: [START] isImage: $isImage, Ext: $extension")

        // 2. ПОДГОТОВКА ДАННЫХ (Сжатие для фото, чтение байтов для доков)
        val fileData: ByteArray = withContext(Dispatchers.IO) {
          if (isImage) compressImage(context, uri)
          else contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        }

        if (fileData.isEmpty()) throw Exception("Файл порожній або недоступний")

        // 3. ПУТЬ В STORAGE (Используем системный ID для организации, если нужно)
        val effectiveOsbbIdForStorage = when (role) {
          UserRole.VodokanalUser -> 9999
          UserRole.YtkeUser -> 9998
          UserRole.TboUser -> 9997
          else -> osbbId
        }

        val folder = if (isImage) "chat_images" else "chat_docs"
        val storagePath =
          "$folder/$effectiveOsbbIdForStorage/$addressId/${System.currentTimeMillis()}.$extension"

        // 4. ЗАГРУЗКА В STORAGE
        val downloadUrl = withContext(Dispatchers.IO) {
          chatRepo.uploadChatImage(fileData, storagePath)
        }
        Log.d("YkisLog", "$methodName: [URL_READY] $downloadUrl")

        // 5. ОТПРАВКА В DATABASE (Используем нашу унифицированную логику путей)
        writeToDatabase(
          chatUid = chatUid,
          senderUid = senderUid,
          senderDisplayedName = senderDisplayedName,
          senderLogoUrl = senderLogoUrl,
          senderAddress = senderAddress,
          addressId = addressId,
          imageUrl = if (isImage) downloadUrl else null,
          fileUrl = if (!isImage) downloadUrl else null,
          fileName = if (!isImage) originalFileName else null,
          osbbId = effectiveOsbbIdForStorage, // Передаем системный ID (9999 и т.д.)
          role = role,
          recipientTokens = recipientTokens,
          onComplete = {
            Log.d("YkisLog", "$methodName: [FINISH] Успішно відправлено")
            _selectedImageUri.value = Uri.EMPTY
            _messageText.value = ""
            clearAiSuggestion()
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
      val scaledBitmap = originalBitmap.scale(width, height)

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


  fun setSelectedMessage(message: MessageEntity) {
    _selectedMessage.value = message
  }


  // ChatViewModel.kt
  private fun getChatPath(
    role: UserRole,
    osbbId: Int,
    addressId: Int,
    targetUserUid: String?
  ): String {
    val methodName = "ChatViewModel.getChatPath"
    val myUid = chatRepo.currentUid ?: ""
    val residentUid = targetUserUid ?: myUid
    val serviceInState = selectedService.value.codeName

    Log.d("YkisLog", "$methodName: [INPUT_PARAMS] Role: $role | In_OSBB: $osbbId | In_Addr: $addressId | Target: $targetUserUid")

    // 1. Определение префикса и системного ID
    val (prefix, sysId) = when {
      role == UserRole.VodokanalUser || serviceInState == "WATER_SERVICE" -> {
        Log.d("YkisLog", "$methodName: [MATCH] Detected WATER_SERVICE")
        "WATER_SERVICE" to 9999
      }

      role == UserRole.YtkeUser || serviceInState == "WARM_SERVICE" -> {
        Log.d("YkisLog", "$methodName: [MATCH] Detected WARM_SERVICE")
        "WARM_SERVICE" to 9998
      }

      role == UserRole.TboUser || serviceInState == "GARBAGE_SERVICE" -> {
        Log.d("YkisLog", "$methodName: [MATCH] Detected GARBAGE_SERVICE")
        "GARBAGE_SERVICE" to 9997
      }

      else -> {
        Log.d("YkisLog", "$methodName: [MATCH] Defaulting to OSBB")
        "OSBB" to osbbId
      }
    }

    // 2. Сборка финального пути
    // Если addressId все еще 9997/9999, значит проблема в вызывающем методе (getUsers или openChat)
    val path = "${prefix}_${sysId}_${addressId}_$residentUid"

    if (addressId == sysId) {
      Log.e("YkisLog", "$methodName: [CRITICAL_WARNING] AddrID ($addressId) is IDENTICAL to SysID ($sysId)!")
    }

    Log.d("YkisLog", "ChatPath: [RESULT] $path | Role: $role | Final_SysID: $sysId | Final_AddrID: $addressId")

    return path
  }



  fun openChatWithUser(
    user: UserEntity,
    currentRole: UserRole,
    currentOsbbId: Int
  ) {
    val methodName = "ChatViewModel.openChatWithUser"
    val realAddressId = user.addressId ?: 0

    // 1. Устанавливаем собеседника и чистим старый список сообщений
    _selectedUser.value = user
    _firebaseTest.value = emptyList()

    // 2. Логика системного ID для служб (9999/9998/9997)
    val effectiveOsbbId = when (currentRole) {
      UserRole.VodokanalUser -> 9999
      UserRole.YtkeUser -> 9998
      UserRole.TboUser -> 9997
      else -> currentOsbbId
    }

    // 3. Формируем путь к ветке Firebase
    val chatId = getChatPath(
      role = currentRole,
      osbbId = effectiveOsbbId,
      addressId = realAddressId,
      targetUserUid = user.uid
    )

    // 4. Сброс локальных счетчиков и запуск мониторинга (чтение + статус печати)
    _unreadCounts.update { it + (chatId to 0) }
    markMessagesAsRead(chatId)
    observeTypingStatus(chatId)

    Log.d("YkisLog", "--- $methodName ---")
    Log.d("YkisLog", "Target: ${user.displayName} | Path: $chatId")

    // 5. Запуск прослушивания сообщений
    readFromDatabase(
      role = currentRole,
      senderUid = user.uid ?: "",
      osbbId = effectiveOsbbId,
      addressId = realAddressId
    )
  }




  override fun onCleared() {
    val methodName = "ChatViewModel.onCleared"
    super.onCleared()

    // 1. Очистка трекера поиска чатов (orderByKey.startAt.endAt)
    cleanupTracker()

    // 2. Очистка основных слушателей чата (DatabaseReference -> ValueEventListener)
    if (listeners.isNotEmpty()) {
      listeners.forEach { (ref, listener) ->
        ref.removeEventListener(listener)
      }
      Log.d("YkisLog", "$methodName: [CLEAN] Основные слушатели удалены (${listeners.size})")
      listeners.clear()
    }

    // 3. Очистка счетчиков непрочитанных (Query -> ValueEventListener)
    if (unreadCountListeners.isNotEmpty()) {
      unreadCountListeners.forEach { (query, listener) ->
        query.removeEventListener(listener)
      }
      Log.d("YkisLog", "$methodName: [CLEAN] Счетчики бейджей удалены (${unreadCountListeners.size})")
      unreadCountListeners.clear()
    }

    // 4. Очистка слушателей превью последних сообщений (String -> ValueEventListener)
    if (lastMessageListeners.isNotEmpty()) {
      lastMessageListeners.forEach { (chatId, listener) ->
        // Используем child(chatId), так как ключом в мапе является строка-путь
        chatsReference.child(chatId).removeEventListener(listener)
      }
      Log.d("YkisLog", "$methodName: [CLEAN] Превью последних сообщений удалены (${lastMessageListeners.size})")
      lastMessageListeners.clear()
    }

    // 5. Очистка статуса печати (DatabaseReference -> ValueEventListener)
    if (typingListeners.isNotEmpty()) {
      typingListeners.forEach { (ref, listener) ->
        ref.removeEventListener(listener)
      }
      Log.d("YkisLog", "$methodName: [CLEAN] Статус печати удален (${typingListeners.size})")
      typingListeners.clear()
    }

    // 6. Очистка фоновых задач и путей
    typingJob?.cancel()
    currentChatPath = null

    Log.d("YkisLog", "$methodName: [SUCCESS] Все ресурсы и трекеры Firebase освобождены")
  }


}
