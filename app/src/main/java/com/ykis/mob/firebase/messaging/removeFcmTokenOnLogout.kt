
package com.ykis.mob.firebase.messaging

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging

fun removeFcmTokenOnLogout(previousUid: String?) {
  val methodName = "FCM_Logout"

  if (previousUid.isNullOrBlank()) {
    Log.w("YkisLog", "$methodName: [ABORT] UID отсутствует")
    return
  }

  FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (!task.isSuccessful) {
      Log.e("YkisLog", "$methodName: [ERROR] Не удалось получить токен для удаления")
      return@addOnCompleteListener
    }

    val token = task.result
    val userRef = Firebase.firestore.collection("users").document(previousUid)

    Log.d("YkisLog", "$methodName: [START] Удаление токена для $previousUid")

    userRef.update("fcmTokens", FieldValue.arrayRemove(token))
      .addOnSuccessListener {
        Log.d("YkisLog", "$methodName: [SUCCESS] Токен удален. Теперь можно делать SignOut.")
      }
      .addOnFailureListener { e ->
        // Если тут ошибка "Permission Denied", значит signOut() был вызван слишком рано
        Log.e("YkisLog", "$methodName: [FATAL] Ошибка Firestore: ${e.message}")
      }
  }
}

