package com.ykis.mob.firebase.messaging

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging


fun addFcmToken() {
  val firebaseMessaging = FirebaseMessaging.getInstance()
  firebaseMessaging.token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
      val token = task.result
      val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

      if (currentUserUid != null) {
        val userRef = Firebase.firestore.collection("users").document(currentUserUid)

        // Создаем Map с данными для обновления/создания
        val data = mapOf(
          "fcmTokens" to FieldValue.arrayUnion(token),
          "uid" to currentUserUid // Гарантируем, что поле uid тоже будет
        )

        // set с merge() — это магия: создает документ, если его нет,
        // или обновляет существующий, не затирая другие поля.
        userRef.set(data, SetOptions.merge())
          .addOnSuccessListener {
            Log.d("YkisMob", "addFcmToken() FCM Token (and user doc) successfully synced!")
          }
          .addOnFailureListener { e ->
            Log.w("YkisMob", "addFcmToken() Error syncing FCM Token", e)
          }
      }
    }
  }
}
