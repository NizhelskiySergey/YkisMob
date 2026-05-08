package com.ykis.mob.ui.screens.chat

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat.getString
import androidx.core.graphics.scale
import androidx.lifecycle.viewModelScope
import androidx.room.util.appendPlaceholders
import com.google.firebase.Timestamp
import com.google.firebase.ai.type.content
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.PropertyName
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.ykis.mob.MainApplication
import com.ykis.mob.R
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.UserRole
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.firebase.service.impl.ChatRepository
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.appartment.ListMode
import com.ykis.mob.ui.screens.service.list.TotalServiceDebt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.forEach
import kotlin.collections.map

@Serializable
data class SendNotificationArguments(
  @SerialName("success") override val success: Int = 0,
  @SerialName("message") override val message: String = "",

  @SerialName("tokens") // PHP получит это как массив, если Retrofit настроен правильно
  val recipientTokens: List<String>,

  @SerialName("title")
  val title: String,

  @SerialName("body")
  val body: String,

  @SerialName("chatId") // ОБЯЗАТЕЛЬНО для навигации
  val chatId: String
) : BaseResponse {
}


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
  private val application: MainApplication,
  private val chatRepo: ChatRepository,
  logService: LogService
) : BaseViewModel(logService) { // УДАЛИЛИ KoinComponent


  // Перенаправляем ссылки на репозиторий
  // Ссылки на Realtime Database
  private val chatsReference by lazy { chatRepo.realtime.getReference("chats") }
  private val unreadCountListeners = mutableMapOf<String, ValueEventListener>()

  // Модель ИИ (Gemini)
  private val generativeModel by lazy { chatRepo.aiModel }

  private val _assistantResponse = MutableStateFlow<String?>(null)

  // Хранилище счетчиков: Key = chatId, Value = количество новых сообщений
  val assistantResponse = _assistantResponse.asStateFlow()

  // Для диспетчера: быстрая подсказка на основе входящего сообщения
  private val _quickHint = MutableStateFlow<String?>(null)
  val quickHint = _quickHint.asStateFlow()

  // Хранилище последних сообщений: Key = chatId, Value = MessageEntity
  private var lastTypingSentTime = 0L // Время последней отправки в базу
  private var typingStopJob: Job? = null // Работа для определения конца печати

  private val _selectedService = MutableStateFlow<TotalServiceDebt?>(null)
  val selectedService = _selectedService.asStateFlow()


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


  // _lastMessages у тебя уже должен быть объявлен как MutableStateFlow<Map<String, MessageEntity>>(emptyMap())
// 1. Сначала объявляем входные потоки (StateFlow)
  private val _userIdentifiersWithRole = MutableStateFlow<List<String>>(emptyList())
  private val _rawFetchedProfiles = MutableStateFlow<List<UserEntity>>(emptyList())
  // В ChatViewModel.kt

  // Приватный мутабельный поток
  private val _lastMessages = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

  // ПУБЛИЧНЫЙ поток для подписки из UI (Добавь это!)
  val lastMessages: StateFlow<Map<String, MessageEntity>> = _lastMessages.asStateFlow()

// Далее идет твой searchQuery и userList (combine...)


  private val _searchQuery = MutableStateFlow("")
  val searchQuery = _searchQuery.asStateFlow()

  // 1. УДАЛИ private val _userList = ... (она больше не нужна)

  // 2. Оставляем только реактивный поток
  val userList: StateFlow<List<UserEntity>> = combine(
    _userIdentifiersWithRole,
    _rawFetchedProfiles,
    _lastMessages,
    _searchQuery // 4-й поток для мгновенной фильтрации
  ) { keys, profiles, lastMsgs, query ->
    Log.d("YkisLog", "ChatViewModel.userList: [RECOMBINE] Ветки: ${keys.size} | Поиск: '$query'")

    // 1. Сборка полного списка объектов
    val fullList = keys.mapNotNull { key ->
      val parts = key.split("_")
      if (parts.size < 4) return@mapNotNull null

      val uidFromKey = parts.last()
      val addrIdFromKey = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: 0

      val profile = profiles.find { it.uid == uidFromKey }
      val lastMsg = lastMsgs[key]
      val preview = lastMsg?.text ?: "Немає повідомлень"

      // Приоритет имени: 1. Адрес из сообщения, 2. Имя из профиля, 3. Заглушка
      val finalDisplayName = when {
        !lastMsg?.senderAddress.isNullOrBlank() -> lastMsg.senderAddress
        profile != null -> profile.displayName ?: "Жилець (о/р $addrIdFromKey)"
        else -> "Користувач (о/р $addrIdFromKey)"
      }

      profile?.copy(
        addressId = addrIdFromKey,
        address = preview, // Превью сообщения
        displayName = finalDisplayName
      )
        ?: UserEntity(
          uid = uidFromKey,
          addressId = addrIdFromKey,
          displayName = finalDisplayName,
          address = preview
        )
    }

    // 2. Фильтрация
    if (query.isBlank()) {
      fullList
    } else {
      fullList.filter { user ->
        val nameMatch = user.displayName?.contains(query, ignoreCase = true) == true
        val idMatch = user.addressId.toString().contains(query)
        val msgMatch = user.address?.contains(query, ignoreCase = true) == true
        nameMatch || idMatch || msgMatch
      }
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )


  private val _selectedUser = MutableStateFlow<UserEntity>(UserEntity())
  val selectedUser = _selectedUser.asStateFlow()

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
  private val _pendingPushChatId = MutableStateFlow<String?>(null)
  val pendingPushChatId = _pendingPushChatId.asStateFlow()

  // В ChatViewModel
  private val _selectedServicePrefix = MutableStateFlow<String?>(null)
  val selectedServicePrefix = _selectedServicePrefix.asStateFlow()

  fun setSelectedService(prefix: String) {
    _selectedServicePrefix.value = prefix
  }

  // В ChatViewModel или ApartmentViewModel при клике на организацию
  fun onServiceSelectedForResident(servicePrefix: String) {
    // 1. Запоминаем службу
    setSelectedService(servicePrefix)

    // 2. Переключаем экран на список объектов (наших квартир)
    // Мы используем тот же экран, что и админ, но данные фильтруем только по "моим"
    _uiState.update { it.copy(listMode = ListMode.APARTMENTS) }
  }

  // Удобная функция для получения бейджа конкретной квартиры для выбранной службы
  fun getUnreadCountForApartment(addrId: Int): Int {
    val prefix = _selectedServicePrefix.value ?: return 0
    val myUid = chatRepo.currentUid ?: return 0
    val fullPath = "${prefix}_${addrId}_$myUid"
    return unreadCounts.value[fullPath] ?: 0
  }

  fun selectUserByUid(uid: String) {
    val user = userList.value.find { it.uid == uid }
    if (user != null) {
      _selectedUser.value = user
      Log.d("YkisLog", "ChatVM: [PUSH_SYNC] Пользователь найден и выбран: ${user.displayName}")
    } else {
      Log.w("YkisLog", "ChatVM: [PUSH_SYNC] Пользователь $uid еще не загружен в список")
    }
  }

  fun setPendingPushChatId(id: String?) {
    _pendingPushChatId.value = id
  }

  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
  }

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
    val methodName = "ChatVM.observeTypingStatus"
    val myUid = chatRepo.currentUid ?: return
    if (chatId.isBlank()) return

    val ref = typingReference.child(chatId)

    // Полный сброс перед запуском
    _isPartnerTyping.value = false
    typingJob?.cancel()

    // 1. Очистка старых слушателей
    typingListeners[ref]?.let {
      ref.removeEventListener(it)
      typingListeners.remove(ref)
      Log.d("YkisLog", "[$methodName]: [CLEANUP] Ветка: $chatId")
    }

    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        // Как только пришли любые данные по печати в этой ветке,
        // запускаем "контролера", который будет следить за временем автономно
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
          // Цикл работает, пока в снимке есть хоть кто-то "свежий"
          while (true) {
            val now = System.currentTimeMillis()
            val activeTyping = snapshot.children.any { child ->
              val timestamp = (child.value as? Long) ?: 0L
              child.key != myUid && (now - timestamp) < 6000 // 6 секунд жизни метки
            }

            if (_isPartnerTyping.value != activeTyping) {
              _isPartnerTyping.value = activeTyping
              Log.d("YkisLog", "[$methodName]: [STATUS] Typing: $activeTyping")
            }

            // Если больше никто не печатает - выходим из цикла и гасим джобу
            if (!activeTyping) break

            // Проверяем актуальность каждую секунду
            delay(1000)
          }
        }
      }

      override fun onCancelled(e: DatabaseError) {}
    }

    ref.addValueEventListener(listener)
    typingListeners[ref] = listener
    Log.d("YkisLog", "[$methodName]: [READY] Мониторинг ветки $chatId")
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
    val methodName = "ChatVM.onMessageTextChanged"
    val oldText = _messageText.value
    _messageText.value = newText

    // Обновляем статус только если это не редактирование
    if (_editingMessage.value == null) {
      val now = System.currentTimeMillis()

      if (newText.isNotBlank()) {
        // 1. Если это начало печати (было пусто)
        // ИЛИ если прошло более 4 секунд с последнего обновления в базе
        if (oldText.isBlank() || (now - lastTypingSentTime > 4000)) {
          lastTypingSentTime = now
          setTypingStatus(true)
        }

        // 2. Таймер "Остановки": если пользователь замер на 2.5 сек, гасим статус
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
          delay(2500)
          setTypingStatus(false)
          lastTypingSentTime = 0L // Сбрасываем, чтобы следующее нажатие сразу сработало
        }
      } else {
        // 3. Если текст стерт полностью — убираем статус немедленно
        typingStopJob?.cancel()
        if (oldText.isNotBlank()) {
          setTypingStatus(false)
          lastTypingSentTime = 0L
        }
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


  fun setTypingStatus(isTyping: Boolean) {
    val methodName = "ChatVM.setTypingStatus"

    // 1. Входной контроль: UID
    val myUid = chatRepo.currentUid ?: run {
      Log.e("YkisLog", "[$methodName]: [ERROR] UID пуст, статус игнорируется")
      return
    }

    // 2. Входной контроль: Путь
    // Используем currentChatPath, который устанавливается при входе в чат
    val path = currentChatPath ?: return
    if (path.isBlank()) {
      // Логируем только переход в false, чтобы не спамить при печати в "никуда"
      if (!isTyping) Log.w("YkisLog", "[$methodName]: [SKIP] Путь пуст, сброс не требуется")
      return
    }

    val ref = typingReference.child(path).child(myUid)

    if (isTyping) {
      // Устанавливаем текущее системное время
      // Жилец увидит надпись, если (now - этот_timestamp) < 6 секунд
      ref.setValue(System.currentTimeMillis())
        .addOnFailureListener { e ->
          Log.e("YkisLog", "[$methodName]: [FAIL] Ошибка записи: ${e.message}")
        }

      // Резервное удаление при обрыве связи
      ref.onDisconnect().removeValue()
    } else {
      // Мгновенное удаление метки, чтобы у собеседника надпись исчезла сразу
      ref.removeValue().addOnSuccessListener {
        Log.v("YkisLog", "[$methodName]: [OFF] Метка удалена для $path")
      }
    }
  }


  fun subscribeToUnreadCount(chatKeys: List<String>) {
    val methodName = "ChatVM.subscribeToUnreadCount"

    val myUid = chatRepo.currentUid ?: run {
      Log.e("YkisLog", "[$methodName]: [ABORT] MyUID is NULL")
      return
    }

    Log.d("YkisLog", "[$methodName]: [IN] Ключей на проверку: ${chatKeys.size}")

    chatKeys.forEach { chatId ->
      if (chatId.isBlank()) return@forEach

      // 1. Проверка на дубликаты слушателей
      if (unreadCountListeners.containsKey(chatId)) {
        Log.v("YkisLog", "[$methodName]: [ALREADY_WATCHING] $chatId")
        return@forEach
      }

      Log.i("YkisLog", "[$methodName]: [NEW_WATCHER] Регистрация для: $chatId")

      val chatRef = chatsReference.child(chatId)

      // Принудительно держим ветку в актуальном состоянии (пробиваем кэш)
      chatRef.keepSynced(true)

      // Оптимизированный запрос только по непрочитанным
      val query = chatRef.orderByChild("read").equalTo(false)

      val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
          var foreignUnreadCount = 0
          val snapshotSize = snapshot.childrenCount

          Log.v(
            "YkisLog",
            "[$methodName.onDataChange]: [EVENT] Ветка: $chatId | Snapshot: $snapshotSize"
          )

          snapshot.children.forEach { child ->
            val msgKey = child.key
            val senderUid = child.child("senderUid").value?.toString() ?: ""
            val msgText = child.child("text").value?.toString()?.take(10) ?: "..."

            if (senderUid != myUid && senderUid.isNotBlank()) {
              foreignUnreadCount++
              Log.d(
                "YkisLog",
                "[$methodName]: [FOUND_UNREAD] Ветка: $chatId | Key: $msgKey | From: ${
                  senderUid.takeLast(5)
                }"
              )
            } else {
              Log.v("YkisLog", "[$methodName]: [MY_MSG_SKIP] Свое пропущено. Key: $msgKey")
            }
          }

          // Атомарное обновление мапы бейджей
          _unreadCounts.update { currentMap ->
            val newMap = currentMap + (chatId to foreignUnreadCount)
            Log.i("YkisLog", "[$methodName]: [MAP_UPDATE] $chatId -> Badge: $foreignUnreadCount")
            newMap
          }
          updateSystemIconBadge()
        }

        override fun onCancelled(error: DatabaseError) {
          Log.e("YkisLog", "[$methodName]: [FIREBASE_ERROR] $chatId: ${error.message}")
        }
      }

      // Запуск мониторинга
      query.addValueEventListener(listener)
      unreadCountListeners[chatId] = listener
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

      // 1. Проверяем, не активен ли уже слушатель для этой ветки
      if (lastMessageListeners.containsKey(chatId)) {
        // Log.v("YkisLog", "$methodName: [SKIP] Уже слушаем $chatId")
        return@forEach
      }

      // 2. Формируем запрос: Сортировка по ключу (времени) + последний 1 элемент
      val lastMsgQuery = chatsReference.child(chatId)
        .orderByKey()
        .limitToLast(1)

      Log.d("YkisLog", "$methodName: [WATCH] Регистрация превью для: $chatId")

      val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
          // Извлекаем последнее сообщение из набора (даже если там всего один элемент)
          val message = snapshot.children.lastOrNull()?.getValue(MessageEntity::class.java)

          if (message != null) {
            _lastMessages.update { currentMap ->
              currentMap + (chatId to message)
            }
            Log.d(
              "YkisLog",
              "$methodName: [UPDATE] Ключ: $chatId | Текст: ${message.text?.take(20)}..."
            )
          } else {
            Log.w(
              "YkisLog",
              "$methodName: [EMPTY] Ветка пуста или сообщение не распознано: $chatId"
            )
          }
        }

        override fun onCancelled(error: DatabaseError) {
          Log.e("YkisLog", "$methodName: [ERROR] Firebase $chatId: ${error.message}")
        }
      }

      // 3. Регистрация и сохранение ссылки для последующей очистки
      lastMsgQuery.addValueEventListener(listener)
      lastMessageListeners[chatId] = listener
    }
  }


  @OptIn(DelicateCoroutinesApi::class)
  fun markMessagesAsRead(chatId: String) {
    val methodName = "ChatVM.markRead"

    val myUid = chatRepo.currentUid ?: run {
      Log.e("YkisLog", "[$methodName]: [ABORT] MyUID is NULL")
      return
    }

    if (chatId.isBlank()) return

    // Используем GlobalScope, чтобы навигация не убила запись "прочитано" в базу
    GlobalScope.launch(Dispatchers.IO) {
      Log.d("YkisLog", "[$methodName]: [1. GLOBAL_START] Ветка: $chatId")

      try {
        // NonCancellable гарантирует, что если мы начали обновлять базу, мы закончим
        withContext(NonCancellable) {
          val ref = chatsReference.child(chatId)
          val snapshot = ref.limitToLast(100).get().await()

          if (!snapshot.exists()) return@withContext

          val updates = mutableMapOf<String, Any>()
          var incomingUnread = 0

          snapshot.children.forEach { child ->
            val msgKey = child.key ?: return@forEach
            val senderUid = child.child("senderUid").value?.toString() ?: ""
            val isRead = child.child("read").value as? Boolean ?: false

            // Нас интересуют ТОЛЬКО входящие, которые еще не прочитаны
            if (!isRead && senderUid != myUid && senderUid.isNotBlank()) {
              updates["$msgKey/read"] = true
              incomingUnread++
            }
          }

          if (updates.isNotEmpty()) {
            Log.d(
              "YkisLog",
              "[$methodName]: [2. DB_WRITE] Пометка прочитанными: $incomingUnread сообщ."
            )
            ref.updateChildren(updates).await()

            withContext(Dispatchers.Main) {
              _unreadCounts.update { it + (chatId to 0) }
              Log.i("YkisLog", "[$methodName]: [3. SUCCESS] Статус в базе обновлен")
              val totalUnread = _unreadCounts.value.values.sum()
              updateSystemIconBadge()

              Log.i("YkisLog", "[$methodName]: [ICON_SYNC] На иконке теперь: $totalUnread")
            }

          } else {
            Log.d("YkisLog", "[$methodName]: [SKIP] Новых входящих нет")
            withContext(Dispatchers.Main) {
              _unreadCounts.update { it + (chatId to 0) }
            }
          }
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "[$methodName]: [FATAL] Ошибка записи: ${e.message}")
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

  fun initResidentChats(
    uid: String,
    osbbId: Int,
    addressId: Int,
    addressText: String,
    nanim: String
  ) {
    val methodName = "ChatViewModel.initResidentChats"
    Log.d("YkisLog", "$methodName: [START] Принудительная сборка 4-х веток")

    // Карта: Префикс службы -> Системный ID
    val serviceMap = mapOf(
      "OSBB" to osbbId,
      "WATER_SERVICE" to 9999,
      "WARM_SERVICE" to 9998,
      "GARBAGE_SERVICE" to 9997
    )

    serviceMap.forEach { (prefix, sysId) ->
      // 1. ПРЯМАЯ СБОРКА ПУТИ (БЕЗ getChatPath)
      // Формат: PREFIX_SYSID_ADDRID_UID
      val chatPath = "${prefix}_${sysId}_${addressId}_$uid"

      Log.d("YkisLog", "$methodName: [PROCESS] Цель: $chatPath")

      val welcomeText = "Вітаю! Чат активовано."

      // 2. Формируем объект сообщения
      val messageKey = chatsReference.child(chatPath).push().key ?: return@forEach
      val messageEntity = MessageEntity(
        id = messageKey,
        senderUid = uid,
        text = welcomeText,
        type = "TEXT",
        senderDisplayedName = nanim,
        senderAddress = addressText,
        timestamp = System.currentTimeMillis(),
        read = false // Чтобы у админов загорелся Badge
      )

      // 3. Запись в Firebase
      chatsReference.child(chatPath).child(messageKey).setValue(messageEntity)
        .addOnSuccessListener {
          Log.d("YkisLog", "$methodName: [SUCCESS] Создана ветка: $chatPath")
        }
        .addOnFailureListener {
          Log.e("YkisLog", "$methodName: [ERROR] Ошибка для $chatPath: ${it.message}")
        }
    }
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

    val displayText =
      if (fileUrl != null && messageText.value.isBlank()) "[Файл]" else messageText.value
    // ... внутри writeToDatabase ...

// Определяем, кто отправляет: жилец или представитель организации
    // В ChatViewModel.writeToDatabase
    // Внутри ChatViewModel.writeToDatabase
    val finalDisplayName = if (role != UserRole.StandardUser) {
      // Получаем строку из ресурсов Android
      when (role) {
        UserRole.VodokanalUser -> application.getString(R.string.vodokanal)
        UserRole.YtkeUser -> application.getString(R.string.ytke_short)
        UserRole.TboUser -> application.getString(R.string.yzhtrans)
        UserRole.OsbbUser -> "Адміністратор ОСББ"
        else -> "Адміністратор"
      }
    } else {
      senderDisplayedName.substringAfter("|").trim()
    }


// 5. Формируем объект сообщения
    val messageEntity = MessageEntity(
      id = messageKey,
      senderUid = senderUid,
      text = displayText,
      type = if (imageUrl != null) "IMAGE" else if (fileUrl != null) "FILE" else "TEXT",
      senderLogoUrl = senderLogoUrl,
      senderDisplayedName = finalDisplayName, // ТУТ ТЕПЕРЬ "Адміністратор"
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
//        val testTokens = listOf("foHIm_F6R2u0Q0nZeOYyMt:APA91bEc8Sg7yTdyOppPVYxaVLeKnA-kHPiVvq7Sj0n3sGxdh42Fdf-Fnahk521Igxr2fDStYrXX2Oy0zvdGsCkqv7u3uexQRF_b8GJkf5cgDVqaEnzle48")


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
  private var presenceRef: DatabaseReference? = null

  fun setPresence(chatId: String, isOnline: Boolean) {
    val methodName = "ChatVM.setPresence"

    // 1. Проверка UID
    val myUid = chatRepo.currentUid ?: run {
      Log.e("YkisLog", "[$methodName]: [ERROR] UID пуст. Статус $isOnline для $chatId ОТМЕНЕН")
      return
    }

    // 2. Формируем ссылку (используем тот же экземпляр базы, что и для чатов)
    val ref = chatRepo.realtime.getReference("presence").child(chatId).child(myUid)

    if (isOnline) {
      // Подробный лог для сравнения путей
      Log.i(
        "YkisLog",
        "[$methodName]: [ON] Установка ONLINE | UID: ${myUid.takeLast(5)} | Path: presence/$chatId"
      )

      ref.setValue(true)
        .addOnSuccessListener {
          Log.d("YkisLog", "[$methodName]: [SUCCESS] Статус ONLINE подтвержден базой")
        }
        .addOnFailureListener {
          Log.e("YkisLog", "[$methodName]: [FAIL] Ошибка записи: ${it.message}")
        }

      // Авто-удаление при закрытии приложения или обрыве связи
      ref.onDisconnect().removeValue()
    } else {
      Log.i("YkisLog", "[$methodName]: [OFF] Удаление метки (OFFLINE) | Path: presence/$chatId")
      ref.removeValue().addOnSuccessListener {
        Log.d("YkisLog", "[$methodName]: [SUCCESS] Статус OFFLINE подтвержден")
      }
    }
  }


  @OptIn(DelicateCoroutinesApi::class)
  fun readFromDatabase(role: UserRole, senderUid: String, osbbId: Int, addressId: Int) {
    val methodName = "ChatVM.readFromDatabase"

    val isScopeActive = viewModelScope.isActive
    Log.i(
      "YkisLog",
      "[$methodName]: [EXTERNAL_CALL] Addr: $addressId | ScopeActive: $isScopeActive"
    )

    // Используем GlobalScope, чтобы старт НЕ ЗАВИСЕЛ от мертвого viewModelScope
    // Это гарантирует выполнение блока инициализации
    GlobalScope.launch(Dispatchers.Main) {
      Log.d("YkisLog", "[$methodName]: [1. GLOBAL_LAUNCH_START] Корутина принудительно запущена")

      try {
        // NonCancellable дополнительно страхует от системных прерываний
        withContext(NonCancellable) {
          Log.d(
            "YkisLog",
            "[$methodName]: [2. NON_CANCELLABLE] Вход в защищенный блок инициализации"
          )

          delay(150)

          var activeUid = chatRepo.currentUid
          var attempts = 0
          while (activeUid == null && attempts < 30) {
            attempts++
            delay(100)
            activeUid = chatRepo.currentUid
          }

          if (activeUid == null) {
            Log.e("YkisLog", "[$methodName]: [ABORT] UID не найден")
            return@withContext
          }

          val effectiveOsbbId = when (role) {
            UserRole.VodokanalUser -> 9999
            UserRole.YtkeUser -> 9998
            UserRole.TboUser -> 9997
            else -> osbbId
          }

          val targetPath = getChatPath(
            role,
            effectiveOsbbId,
            addressId,
            if (role != UserRole.StandardUser) senderUid else null
          )
          val currentRef = chatsReference.child(targetPath)

          if (currentChatPath == targetPath && listeners.containsKey(currentRef) && _firebaseTest.value.isNotEmpty()) {
            Log.d("YkisLog", "[$methodName]: [SKIP] Ветка актуальна")
            setPresence(targetPath, true)
            markMessagesAsRead(targetPath)
            return@withContext
          }

          Log.i(
            "YkisLog",
            "[$methodName]: [3. FORCE_INIT] Установка слушателя Firebase на: $targetPath"
          )
          currentChatPath = targetPath

          listeners.forEach { (ref, l) -> ref.removeEventListener(l) }
          listeners.clear()

          val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
              if (currentChatPath != targetPath) return

              // Для обновления UI используем GlobalScope или MainScope экрана,
              // так как viewModelScope здесь всё еще может быть мертв.
              // Но лучше привязаться к Main, Firebase всё равно вернет данные.
              GlobalScope.launch(Dispatchers.Main) {
                Log.v(
                  "YkisLog",
                  "[$methodName.onDataChange]: [DATA_RECEIVED] База=${snapshot.childrenCount}"
                )

                val messages = withContext(Dispatchers.Default) {
                  snapshot.children.mapNotNull { it.getValue(MessageEntity::class.java) }
                    .filter { msg ->
                      val deleted = msg.deletedFor ?: emptyList()
                      !deleted.contains(activeUid)
                    }.sortedBy { it.timestamp }
                }

                _firebaseTest.value = messages.toList()
                Log.d("YkisLog", "[$methodName]: [RENDER_COMPLETE] Итог: ${messages.size}")

                markMessagesAsRead(targetPath)
                setPresence(targetPath, true)
              }
            }

            override fun onCancelled(error: DatabaseError) {
              Log.e("YkisLog", "[$methodName]: [CANCELLED] ${error.message}")
            }
          }

          currentRef.keepSynced(true)
          currentRef.addValueEventListener(listener)
          listeners[currentRef] = listener
          observeTypingStatus(targetPath)
        }
      } catch (e: Exception) {
        Log.e("YkisLog", "[$methodName]: [CRASH] Ошибка: ${e.message}")
      } finally {
        Log.d("YkisLog", "[$methodName]: [4. GLOBAL_LAUNCH_END] Корутина завершена")
      }
    }
  }


  fun clearCurrentChatPath() {
    val methodName = "ChatVM.clearCurrentChatPath"
    val chatId = currentChatPath // Запоминаем путь перед очисткой

    if (chatId == null) {
      Log.d("YkisLog", "$methodName: [SKIP] Путь уже пуст, чистка не требуется")
      return
    }

    Log.d("YkisLog", "$methodName: [START] Выход из чата: $chatId")

    // 1. Сбрасываем локальные флаги UI
    currentChatPath = null
    _isPartnerTyping.value = false

    // 2. Убираем статус "печатает..." в Firebase
    setTypingStatus(false)

    // 3. КРИТИЧНО ДЛЯ ПУШЕЙ: Убираем статус присутствия (Presence)
    // Теперь сервер будет знать, что мы вышли, и при новом сообщении пришлет Push
    setPresence(chatId, false)

    // 4. Чистим слушатели (чтобы не потреблять трафик в фоне)
    if (listeners.isNotEmpty()) {
      Log.d("YkisLog", "$methodName: [CLEANUP] Удаление слушателей сообщений для: $chatId")
      listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
      listeners.clear()
    }

    Log.i("YkisLog", "$methodName: [SUCCESS] Контекст чата полностью очищен")
  }


  fun deleteChatThreads(uid: String, osbbId: Int, addressId: Int) {
    val methodName = "ChatViewModel.deleteChatThreads"

    // Логируем входящие значения до начала корутины
    Log.d("YkisLog", "$methodName: [INPUT] UID=$uid, OSBB_ID=$osbbId, ADDR_ID=$addressId")


    // Используем NonCancellable, чтобы навигация не прервала удаление
    viewModelScope.launch(Dispatchers.IO + NonCancellable) {
      val chatKeys = listOf(
        "OSBB_${osbbId}_${addressId}_$uid",
        "WATER_SERVICE_9999_${addressId}_$uid",
        "WARM_SERVICE_9998_${addressId}_$uid",
        "GARBAGE_SERVICE_9997_${addressId}_$uid"
      )

      Log.d("YkisLog", "$methodName: [START] Попытка удаления ${chatKeys.size} веток")

      chatKeys.forEach { chatPath ->
        try {
          Log.d("YkisLog", "$methodName: [PROCESS] Удаление ветки: $chatPath")

          // Сама операция удаления
          chatsReference.child(chatPath).removeValue().await()

          Log.d("YkisLog", "$methodName: [SUCCESS] Ветка удалена: $chatPath")
        } catch (e: Exception) {
          Log.e("YkisLog", "$methodName: [ERROR] Ошибка при удалении $chatPath: ${e.message}")
        }
      }
      Log.d("YkisLog", "$methodName: [FINISH] Процесс очистки завершен")
    }
  }


  /**
   * Отслеживает список активных чатов (ID пользователей) для конкретной роли админа.
   * Например: админ ОСББ 105 увидит только ветки, начинающиеся на "OSBB_105_".
   */
  fun trackUserIdentifiersWithRole(role: UserRole, osbbId: Int?) {
    val methodName = "ChatVM.trackUserIdentifiers"

    Log.d("YkisLog", "[$methodName]: [ENTRY] Role: $role | OsbbId: $osbbId")

    if (role == UserRole.StandardUser) {
      Log.d("YkisLog", "[$methodName]: [CANCEL] Жильцу трекер не нужен")
      return
    }

    // 1. Формируем префикс для поиска веток
    val targetPrefix = when (role) {
      UserRole.VodokanalUser -> "WATER_SERVICE_9999_"
      UserRole.YtkeUser -> "WARM_SERVICE_9998_"
      UserRole.TboUser -> "GARBAGE_SERVICE_9997_"
      UserRole.OsbbUser -> "OSBB_${osbbId ?: 0}_"
      else -> "UNKNOWN_"
    }

    Log.d("YkisLog", "[$methodName]: [CONFIG] Поиск по префиксу: '$targetPrefix'")

    // 2. Очистка старого слушателя диапазона (данные в StateFlow сохраняем)
    cleanupTracker()
    Log.d(
      "YkisLog",
      "[$methodName]: [CLEAN] Слушатель диапазона снят. Бейджи в кэше: ${_unreadCounts.value.size}"
    )

    // 3. Создаем запрос по диапазону ключей
    val query = chatsReference.orderByKey()
      .startAt(targetPrefix)
      .endAt(targetPrefix + "\uf8ff")

    val listener = object : ValueEventListener {
      override fun onDataChange(dataSnapshot: DataSnapshot) {
        val chatKeys = dataSnapshot.children.mapNotNull { it.key }
        Log.d("YkisLog", "[$methodName]: [DATA] База вернула ${chatKeys.size} веток")

        viewModelScope.launch(Dispatchers.Default) {
          val currentKeys = _userIdentifiersWithRole.value

          // Проверки состояния
          val keysIdentical = chatKeys.sorted() == currentKeys.sorted()
          val uiPopulated = userList.value.isNotEmpty()
          val badgesLoaded = _unreadCounts.value.isNotEmpty()

          Log.v(
            "YkisLog",
            "[$methodName]: [CHECK] Ключи совпали: $keysIdentical | UI готов: $uiPopulated | Бейджи есть: $badgesLoaded"
          )

          withContext(Dispatchers.Main) {
            _userIdentifiersWithRole.value = chatKeys

            if (chatKeys.isNotEmpty()) {
              Log.i("YkisLog", "[$methodName]: [REFRESH] Очистка и перезапуск бейджей для Админа")

              // КРИТИЧЕСКИЙ ФИКС: Удаляем старых слушателей бейджей, чтобы создать новые
              unreadCountListeners.forEach { (id, listener) ->
                chatsReference.child(id).removeEventListener(listener)
              }
              unreadCountListeners.clear()

              // Теперь запускаем подписки заново
              subscribeToUnreadCount(chatKeys)
              subscribeToLastMessages(chatKeys)

              if (!keysIdentical || !uiPopulated) {
                getUsers()
              }
            } else {
              Log.w(
                "YkisLog",
                "[$methodName]: [EMPTY] Чаты по префиксу '$targetPrefix' отсутствуют"
              )
              _rawFetchedProfiles.value = emptyList()
              _unreadCounts.value = emptyMap()
            }
          }
        }
        updateSystemIconBadge()
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("YkisLog", "[$methodName]: [FIREBASE_ERROR] ${error.message}")
      }
    }

    // 4. Активация слушателя
    query.addValueEventListener(listener)
    activeTrackerQuery = query
    activeTrackerListener = listener
    Log.d("YkisLog", "[$methodName]: [ACTIVE] Мониторинг веток запущен")
  }

  // В ChatViewModel
  private fun updateSystemIconBadge() {
    val methodName = "ChatVM.updateSystemIconBadge"
    try {
      // 1. Считаем общую сумму из нашего StateFlow
      val totalCount = _unreadCounts.value.values.sum()
      Log.d("YkisLog", "[$methodName]: [ICON_UPDATE] Итого к отрисовке: $totalCount")

      // 2. Используем 'application' вместо 'context'
      me.leolin.shortcutbadger.ShortcutBadger.applyCount(application, totalCount)

      // 3. Чистим шторку уведомлений, если всё прочитано
      if (totalCount == 0) {
        val notificationManager =
          application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
        Log.d("YkisLog", "[$methodName]: [NOTIF_CLEAN] Шторка очищена")
      }
    } catch (e: Exception) {
      Log.e("YkisLog", "[$methodName]: [ERROR] ${e.message}")
    }
  }


  // В ChatViewModel


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
      return
    }

    viewModelScope.launch {
      try {
        Log.d("YkisLog", "--- $methodName [START] ---")

        // 1. Извлекаем уникальные UID из ключей веток
        val uidsToFetch = chatKeys.map { it.substringAfterLast("_") }
          .filter { it.isNotEmpty() }
          .distinct()

        // 2. Загружаем профили из репозитория
        val fetchedProfiles = withContext(Dispatchers.IO) {
          Log.d("YkisLog", "$methodName: Загрузка профилей для UID: $uidsToFetch")
          chatRepo.fetchUsersByIds(uidsToFetch)
        }

        // 3. Обновляем данные для реактивного combine
        withContext(Dispatchers.Main) {
          Log.d("YkisLog", "$methodName: [SUCCESS] Загружено ${fetchedProfiles.size} профилей")

          // Это "толкнет" combine, который соберет финальный userList
          _rawFetchedProfiles.value = fetchedProfiles

          // 4. Запускаем/обновляем слушатели
          // Важно: сначала запускаем подписки, чтобы мапа _lastMessages начала наполняться
          Log.d("YkisLog", "$methodName: [SYNC] Запуск подписок на бейджи и сообщения")
          subscribeToLastMessages(chatKeys)
          subscribeToUnreadCount(chatKeys)
        }

      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      }
    }
  }


  fun subscribeToResidentCounters(
    uid: String,
    osbbId: Int,
    addressId: Int,
    addressText: String = "",
    nanim: String = "" // Добавь ФИО жильца в параметры, если есть возможность
  ) {
    val methodName = "ChatViewModel.subscribeToResidentCounters"

    // 1. УНИФИЦИРОВАННЫЕ КЛЮЧИ
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

          // Инициализируем только реально пустые ветки
          if (!snapshot.exists() || snapshot.childrenCount == 0L) {
            Log.d(
              "YkisLog",
              "$methodName: [INIT_USER_MSG] Отправка приветствия от жильца в $chatPath"
            )

            val welcomeText = if (addressText.isNotEmpty()) {
              " $addressText (о/р $addressId). Чат активовано."
            } else {
              "Чат активовано."
            }

            // Формируем сообщение ОТ ИМЕНИ ПОЛЬЗОВАТЕЛЯ
            val welcomeMsg = hashMapOf(
              "senderUid" to uid,             // ТЕПЕРЬ ТУТ UID ЖИЛЬЦА
              "senderName" to nanim,          // Имя жильца
              "senderAddress" to addressText, // Весь адрес для админа
              "text" to welcomeText,
              "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP,
              "read" to false,                // Чтобы у админа загорелся бейдж
              "type" to "TEXT"
            )

            // Используем фиксированный ключ "init", чтобы не дублировать при перезаходах
            chatRef.child("init").setValue(welcomeMsg).await()
          }
        } catch (e: Exception) {
          Log.e("YkisLog", "$methodName: [ERROR] Путь $chatPath: ${e.message}")
        }
      }
    }

    // 2. СБОР ТОКЕНОВ АДМИНОВ
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val admins = chatRepo.fetchAdminsByOsbb(osbbId)
        val adminTokens = admins.flatMap { it.tokens ?: emptyList() }.distinct()
        _recipientTokens.value = adminTokens
        Log.d("YkisLog", "$methodName: [TOKENS] Получено токенов: ${adminTokens.size}")
      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [TOKENS_ERROR] ${e.message}")
      }
    }

    // 3. ПОДПИСКА НА СЧЕТЧИКИ
    subscribeToUnreadCount(chatKeys)
  }


  /**
   * Устанавливает текущую выбранную службу для чата на основе данных о долгах.
   * Преобразует категорию контента в строковое имя для формирования ID чата.
   */
  // ChatViewModel.kt
  fun setSelectedService(totalServiceDebt: TotalServiceDebt?) {
    val methodName = "ChatViewModel.setSelectedService"

    if (totalServiceDebt == null) {
      // Сбрасываем оба стейта в null
      _selectedService.value = null
      _selectedServicePrefix.value = null
      Log.d("YkisLog", "$methodName: [RESET] Служба сброшена")
      return
    }

    // 1. Сохраняем весь объект службы (для заголовков и иконок в UI)
    _selectedService.value = totalServiceDebt

    // 2. Извлекаем и сохраняем системный префикс (для путей Firebase)
    val serviceCode = totalServiceDebt.contentDetail.name
    _selectedServicePrefix.value = serviceCode

    Log.d(
      "YkisLog",
      "$methodName: [SELECT] Служба: ${totalServiceDebt.name} | Prefix: $serviceCode"
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
      Log.d("YkisLog", "SEND_PUSH: Target Tokens: $recipientTokens")

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
  fun getChatPath(
    role: UserRole,
    osbbId: Int,
    addressId: Int,
    targetUserUid: String?
  ): String {
    val methodName = "ChatViewModel.getChatPath"
    val myUid = chatRepo.currentUid ?: ""
    val residentUid = targetUserUid ?: myUid
    val serviceInState = selectedService.value?.name

    Log.d(
      "YkisLog",
      "$methodName: [INPUT_PARAMS] Role: $role | In_OSBB: $osbbId | In_Addr: $addressId | Target: $targetUserUid"
    )

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
      Log.e(
        "YkisLog",
        "$methodName: [CRITICAL_WARNING] AddrID ($addressId) is IDENTICAL to SysID ($sysId)!"
      )
    }

    Log.d(
      "YkisLog",
      "$methodName: [RESULT] $path | Role: $role | Final_SysID: $sysId | Final_AddrID: $addressId"
    )

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
//    _firebaseTest.value = emptyList()

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

    // 1. Очистка трекера поиска чатов
    cleanupTracker()

    // 2. Очистка основных слушателей чата
    if (listeners.isNotEmpty()) {
      listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
      Log.d("YkisLog", "$methodName: [CLEAN] Основные слушатели удалены (${listeners.size})")
      listeners.clear()
    }

    // 3. ФИКС: Очистка счетчиков непрочитанных (String -> ValueEventListener)
    if (unreadCountListeners.isNotEmpty()) {
      unreadCountListeners.forEach { (chatId, listener) ->
        // Восстанавливаем Query, который мы слушали (обязательно с теми же фильтрами!)
        val query = chatsReference.child(chatId).orderByChild("read").equalTo(false)
        query.removeEventListener(listener)
      }
      Log.d(
        "YkisLog",
        "$methodName: [CLEAN] Счетчики бейджей удалены (${unreadCountListeners.size})"
      )
      unreadCountListeners.clear()
    }

    // 4. Очистка превью последних сообщений
    if (lastMessageListeners.isNotEmpty()) {
      lastMessageListeners.forEach { (chatId, listener) ->
        chatsReference.child(chatId).removeEventListener(listener)
      }
      Log.d(
        "YkisLog",
        "$methodName: [CLEAN] Превью последних сообщений удалены (${lastMessageListeners.size})"
      )
      lastMessageListeners.clear()
    }

    // 5. Очистка статуса печати
    if (typingListeners.isNotEmpty()) {
      typingListeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
      Log.d("YkisLog", "$methodName: [CLEAN] Статус печати удален (${typingListeners.size})")
      typingListeners.clear()
    }

    // 6. Очистка фоновых задач
    typingJob?.cancel()
    currentChatPath = null

    Log.d("YkisLog", "$methodName: [SUCCESS] Все ресурсы и трекеры Firebase освобождены")
  }


}
