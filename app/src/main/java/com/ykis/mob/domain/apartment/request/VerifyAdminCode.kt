package com.ykis.mob.domain.apartment.request
import com.google.firebase.firestore.FirebaseFirestore
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

class VerifyAdminCode(
  private val firestoreLazy: Lazy<FirebaseFirestore>
) {
  private val firestore get() = firestoreLazy.value

  operator fun invoke(code: String, uid: String): Flow<Resource<Boolean>> = flow {
    emit(Resource.Loading())
    try {
      // 1. Ищем код в коллекции спец-доступов (например, 'access_codes')
      // В этой коллекции документы содержат: 'secretCode', 'role' и 'orgId'
      val snapshot = firestore.collection("access_codes")
        .whereEqualTo("secretCode", code)
        .get()
        .await()

      if (snapshot.isEmpty) {
        emit(Resource.Error("Неверный код доступа. Проверьте правильность ввода."))
        return@flow
      }

      val doc = snapshot.documents.first()

      // 2. Извлекаем роль (строку) и ID организации (ОСББ или ведомства)
      val roleName = doc.getString("role") ?: UserRole.StandardUser.codeName
      val orgId = doc.getLong("orgId")?.toInt() ?: 0

      // 3. Обновляем профиль пользователя в Firestore
      val userUpdate = mapOf(
        "role" to roleName, // "osbb", "vodokanal", "ytke" или "tbo"
        "osbbRoleId" to orgId // Универсальное поле для привязки к организации
      )

      firestore.collection("users")
        .document(uid)
        .update(userUpdate)
        .await()

      emit(Resource.Success(true))
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Ошибка сервера при активации кода"))
    }
  }.flowOn(Dispatchers.IO)
}


