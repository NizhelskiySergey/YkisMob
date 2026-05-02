package com.ykis.mob.domain.apartment.request

import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class DeleteUserAccount(
  private val repository: ApartmentRepository,
) {
  operator fun invoke(uid: String, email: String): Flow<Resource<GetSimpleResponse>> = flow {
    val methodName = "UseCase.DeleteUserAccount"

    try {
      Log.d("YkisLog", "$methodName: [START] Запрос на удаление UID: $uid")
      emit(Resource.Loading())

      // ЗАПРОС В СЕТЬ (MySQL)
      val response = repository.deleteUserAccount(uid, email)

      Log.d("YkisLog", "$methodName: [RESPONSE] Success: ${response.success}, Message: ${response.message}")

      if (response.success == 1) {
        Log.d("YkisLog", "$methodName: [SUCCESS] Данные удалены из внешней БД")
        emit(Resource.Success(response))
      } else {
        Log.e("YkisLog", "$methodName: [API_REJECT] Сервер отказал в удалении: ${response.message}")
        // Показываем общую ошибку удаления из ресурсов
        emit(Resource.Error(resourceMessage = R.string.error_delete_account))
      }

    } catch (e: java.io.IOException) {
      // СЛУЧАЙ: СЕРВИС ЛЕЖИТ ИЛИ НЕТ СЕТИ
      Log.e("YkisLog", "$methodName: [NETWORK_FAIL] Ошибка связи при удалении")
      emit(Resource.Error(
        resourceMessage = R.string.error_network,
        message = "Неможливо видалити профіль без стабільного з'єднання"
      ))

    } catch (ce: kotlinx.coroutines.CancellationException) {
      Log.w("YkisLog", "$methodName: [CANCELLED] Процесс прерван")
      throw ce // Обязательно пробрасываем дальше для корутин

    } catch (ex: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL] Непредвиденная ошибка: ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage ?: "Непередбачена помилка"))
    }
  }.flowOn(Dispatchers.IO)
}

