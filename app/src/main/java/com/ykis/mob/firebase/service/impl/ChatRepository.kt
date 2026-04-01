package com.ykis.mob.firebase.service.impl

import android.util.Log
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.ykis.mob.ui.screens.chat.MessageEntity
import com.ykis.mob.ui.screens.chat.UserEntity
import com.ykis.mob.ui.screens.chat.mapToUserEntity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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
  suspend fun uploadChatImage(imageData: ByteArray, chatId: String): String {
    val fileName = "${System.currentTimeMillis()}_image.jpg"
    val photoRef = storage.reference
      .child("chat_images")
      .child(chatId.replace("/", "_"))
      .child(fileName)

    // Загрузка и получение URL в одном suspend-методе
    photoRef.putBytes(imageData).await()
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
