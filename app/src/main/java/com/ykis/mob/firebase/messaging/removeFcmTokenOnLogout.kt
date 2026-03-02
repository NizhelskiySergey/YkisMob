
package com.ykis.mob.firebase.messaging

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessaging

fun removeFcmTokenOnLogout(previousUid : String?) {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val token = task.result
            Log.d("FCM", "previousUid : $previousUid")
            Log.d("FCM", "token $token")
            if (previousUid != null && token != null) {
                val userRef = Firebase.firestore.collection("users").document(previousUid)

                // Видаляємо токен з масиву fcmTokens
                userRef.update("fcmTokens", FieldValue.arrayRemove(token))
                    .addOnSuccessListener {
                        Log.d("FCM", "FCM Token successfully removed!")
                    }
                    .addOnFailureListener { e ->
                        Log.w("FCM", "Error removing FCM Token", e)
                    }
            }
        } else {
            Log.w("FCM", "Failed to get FCM token for removal")
        }
    }
}
