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
    val methodName = "DeleteUserAccount.invoke"
    emit(Resource.Loading())

    try {
      Log.d("YkisLog", "$methodName: [START] Запрос на удаление UID: $uid")

      // Прямой suspend запрос к API (MySQL)
      val response = repository.deleteUserAccount(uid, email)

      if (response.success == 1) {
        Log.d("YkisLog", "$methodName: [SUCCESS] Аккаунт удален из внешней БД (MySQL)")
        emit(Resource.Success(response))
      } else {
        Log.e("YkisLog", "$methodName: [API_ERROR] Success: ${response.success}")
        emit(Resource.Error(resourceMessage = R.string.error_delete_account))
      }

    } catch (ce: CancellationException) {
      Log.w("YkisLog", "$methodName: [CANCELLED] Процесс отменен корутиной")
      throw ce
    } catch (ex: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO)
}
