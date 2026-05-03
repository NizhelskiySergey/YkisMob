package com.ykis.mob.firebase.messaging

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging


fun addFcmToken() {
  val methodName = "FCM_Update"
  val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

  if (currentUserUid == null) {
    Log.w("YkisLog", "$methodName: [ABORT] Пользователь не авторизован")
    return
  }

  FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (!task.isSuccessful) {
      Log.e("YkisLog", "$methodName: [ERROR] Не удалось получить токен", task.exception)
      return@addOnCompleteListener
    }

    val token = task.result
    Log.d("YkisLog", "$methodName: [TOKEN_RECV] $token")

    val userRef = Firebase.firestore.collection("users").document(currentUserUid)

    // Используем set с merge, чтобы если документа нет - он создался,
    // а если есть - обновился массив токенов
    userRef.set(
      mapOf("fcmTokens" to FieldValue.arrayUnion(token)),
      SetOptions.merge()
    )
      .addOnSuccessListener {
        Log.d("YkisLog", "$methodName: [SUCCESS] Токен привязан к UID: $currentUserUid")
      }
      .addOnFailureListener { e ->
        Log.e("YkisLog", "$methodName: [FATAL] Ошибка Firestore: ${e.message}")
      }
  }
}
