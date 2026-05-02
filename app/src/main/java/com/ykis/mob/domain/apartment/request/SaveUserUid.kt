package com.ykis.mob.domain.apartment.request

import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.cancellation.CancellationException

class SaveUserUid(
  private val repository: ApartmentRepository,
) {
  operator fun invoke(uid: String, email: String): Flow<Resource<GetSimpleResponse>> = flow {
    val methodName = "UseCase.SaveUserUid"
    try {
      Log.d("YkisLog", "$methodName: [START] Регистрация UID: $uid для Email: $email")
      emit(Resource.Loading())

      // 1. ЗАПРОС В СЕТЬ (MySQL API)
      val response = repository.saveUserUid(uid, email)

      Log.d("YkisLog", "$methodName: [RESPONSE] Success: ${response.success}, Message: ${response.message}")

      // 2. ОБРАБОТКА РЕЗУЛЬТАТА
      val result = when {
        response.success == 1 -> {
          Log.d("YkisLog", "$methodName: [SUCCESS] UID успешно сохранен в MySQL")
          Resource.Success(response)
        }
        response.message == "UserUIdExist" -> {
          Log.w("YkisLog", "$methodName: [SKIP] UID уже существует в базе")
          Resource.Error(resourceMessage = R.string.user_uid_exist)
        }
        response.message == "SaveUserUidError" -> {
          Log.e("YkisLog", "$methodName: [SERVER_ERROR] Ошибка на стороне PHP/MySQL")
          Resource.Error(resourceMessage = R.string.error_save_uid)
        }
        else -> {
          Log.e("YkisLog", "$methodName: [UNKNOWN_ERROR] Ответ: ${response.message}")
          Resource.Error(resourceMessage = R.string.error_add_user)
        }
      }
      emit(result)

    } catch (e: java.io.IOException) {
      // СЛУЧАЙ: ТВОЙ СЕРВИС ЛЕЖИТ / НЕТ СЕТИ
      Log.e("YkisLog", "$methodName: [NETWORK_FAIL] Сервис недоступен (IOException)")
      emit(Resource.Error(
        resourceMessage = R.string.error_network,
        message = "Неможливо зв'язатися з сервером авторизації"
      ))

    } catch (ce: kotlinx.coroutines.CancellationException) {
      Log.w("YkisLog", "$methodName: [CANCELLED] Процесс прерван корутиной")
      throw ce // Пробрасываем для корректного завершения Flow

    } catch (ex: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL] Непередбачена помилка: ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage ?: "Помилка реєстрації"))
    }
  }.flowOn(Dispatchers.IO)
}






