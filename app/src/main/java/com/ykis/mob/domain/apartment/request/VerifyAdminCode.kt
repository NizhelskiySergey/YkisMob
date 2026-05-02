package com.ykis.mob.domain.apartment.request

import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class VerifyAdminCode(
  private val repository: ApartmentRepository
) {
  operator fun invoke(code: String, uid: String): Flow<Resource<GetSimpleResponse>> = flow {
    val methodName = "UseCase.VerifyAdminCode"

    try {
      Log.d("YkisLog", "$methodName: [START] Проверка кода для UID: $uid")
      emit(Resource.Loading())

      // ЗАПРОС В СЕТЬ (к вашему MySQL API)
      val response = repository.verifyAdminSecretWord(code, uid)

      Log.d("YkisLog", "$methodName: [RESPONSE] Success: ${response.success}, Role: ${response.userRole}, OsbbId: ${response.osbbId}")

      if (response.success == 1) {
        Log.d("YkisLog", "$methodName: [SUCCESS] Код подтвержден. Роль: ${response.userRole}")
        emit(Resource.Success(response))
      } else {
        Log.w("YkisLog", "$methodName: [REJECT] Неверный секретный код")
        throw ExceptionWithResourceMessage(R.string.error_incorrect_admin_code)
      }

    } catch (e: java.io.IOException) {
      // СЛУЧАЙ: ВАШ СЕРВИС ЛЕЖИТ / НЕТ ИНТЕРНЕТА
      Log.e("YkisLog", "$methodName: [NETWORK_FAIL] Ошибка связи (IOException)")
      emit(Resource.Error(
        resourceMessage = R.string.error_network,
        message = "Неможливо перевірити код без з'єднання з сервером"
      ))

    } catch (e: ExceptionWithResourceMessage) {
      // Ошибка логики (неверный код)
      emit(Resource.Error(resourceMessage = e.resourceMessage))

    } catch (ex: Exception) {
      // Критическая ошибка парсинга или кода
      Log.e("YkisLog", "$methodName: [FATAL] Непередбачена помилка: ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage ?: "Помилка сервера"))
    }
  }.flowOn(Dispatchers.IO)
}

