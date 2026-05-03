package com.ykis.mob.firebase.service.impl

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.ykis.mob.domain.apartment.ApartmentRepository
import com.ykis.mob.firebase.entity.UserFirebase
import com.ykis.mob.firebase.entity.toEntity
import com.ykis.mob.ui.screens.chat.MessageEntity
import com.ykis.mob.ui.screens.chat.UserEntity
import com.ykis.mob.ui.screens.chat.mapToUserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.Lazy

class ChatRepository (
  private val firestore:FirebaseFirestore,
  val realtime: FirebaseDatabase,
  val storage: FirebaseStorage,
  private val functions: FirebaseFunctions,
  val aiModel: GenerativeModel,
) {
//  private val realtime: FirebaseDatabase by realtimeLazy
val currentUid: String?
  get() = Firebase.auth.currentUser?.uid

  suspend fun fetchUsersByIds(ids: List<String>): List<UserEntity> {
    if (ids.isEmpty()) return emptyList()

    // Firestore позволяет до 30 элементов в whereIn
    val idsToFetch = ids.distinct().take(30)

    return try {
      val result = firestore.collection("users")
        .whereIn(FieldPath.documentId(), idsToFetch)
        .get()
        .await() // Используем корутины вместо слушателей

      result.documents.mapNotNull { doc ->
        mapToUserEntity(doc.id, doc.data ?: emptyMap())
      }
    } catch (e: Exception) {
      Log.e("ChatRepository", "Error fetching users", e)
      throw e
    }
  }
  // В классе ChatRepository
  fun observeLastMessage(chatUid: String): Flow<MessageEntity?> = callbackFlow {
    val reference = realtime.getReference("chats").child(chatUid).limitToLast(1)

    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        val message = snapshot.children.lastOrNull()?.getValue(MessageEntity::class.java)
        trySend(message) // Отправляем данные в поток Flow
      }

      override fun onCancelled(error: DatabaseError) {
        close(error.toException())
      }
    }

    reference.addValueEventListener(listener)
    // Важно для Kotzilla: предотвращаем утечки памяти, удаляя слушатель
    awaitClose { reference.removeEventListener(listener) }
  }
  // В твоем репозитории
  suspend fun fetchAdminsByOsbb(osbbId: Int): List<UserEntity> = withContext(Dispatchers.IO) {
    try {
      // Запрос в коллекцию "users"
      val snapshot = firestore.collection("users")
        .whereEqualTo("osbbId", osbbId)
        .whereIn("userRole", listOf("OsbbUser", "VodokanalUser", "YtkeUser", "TboUser"))
        .get()
        .await()

      snapshot.toObjects(UserFirebase::class.java).map { it.toEntity() }
    } catch (e: Exception) {
      Log.e("YkisLog", "Error fetching admins for OSBB $osbbId: ${e.message}")
      emptyList()
    }
  }
  suspend fun getUserByUid(uid: String): UserEntity? {
    return try {
      // Имя коллекции должно совпадать с твоим в Firebase
      val snapshot = firestore.collection("users").document(uid).get().await()
      snapshot.toObject(UserEntity::class.java)
    } catch (e: Exception) {
      null
    }
  }
  suspend fun deleteImageFromStorage(imageUrl: String) {
    try {
      // Создаем ссылку на файл из строки URL
      val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
      storageRef.delete().await()
      Log.d("YkisLog", "Storage: Файл успешно удален: $imageUrl")
    } catch (e: Exception) {
      Log.e("YkisLog", "Storage: Ошибка удаления файла: ${e.message}")
    }
  }

  suspend fun uploadChatImage(imageData: ByteArray, storagePath: String): String {
    // Используем переданный полный путь (например, "chat_images/3/1336/123.jpg")
    val photoRef = storage.reference.child(storagePath)

    // Загрузка
    photoRef.putBytes(imageData).await()

    // Получение URL
    return photoRef.downloadUrl.await().toString()
  }

  suspend fun sendPushNotification(token: String, title: String, body: String) {
    val urlString = "https://sendnotification-ai2rm2uxna-uc.a.run.app"

    try {
      functions
        .getHttpsCallableFromUrl(urlString.toHttpUrl().toUrl())
        .call()
        .await()
      Log.d("ChatRepository", "Notification sent successfully to: $token")
    } catch (e: Exception) {
      Log.e("ChatRepository", "Failed to send notification to $token", e)
      throw e
    }
  }
  suspend fun askAiAssistant(userText: String): String? {
    val prompt = "You are an assistant to the homeowner of the HOA. Answer the question: $userText"
    return try {
      val response = aiModel.generateContent(prompt)
      response.text
    } catch (e: Exception) {
      Log.e("ChatRepository", "Gemini error: ${e.message}")
      null
    }
  }


}
