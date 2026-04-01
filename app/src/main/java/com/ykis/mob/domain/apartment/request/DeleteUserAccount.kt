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
    emit(Resource.Loading())

    try {
      // Выполняем прямой suspend запрос
      val response = repository.deleteUserAccount(uid, email)

      val result = when {
        response.success == 1 -> Resource.Success(response)
        else -> Resource.Error(resourceMessage = R.string.error_delete_account)
      }
      emit(result)

    } catch (ce: CancellationException) {
      // ОЧЕНЬ ВАЖНО: пробрасываем исключение отмены дальше,
      // чтобы Flow завершился корректно и не вызывал ошибку прозрачности.
      throw ce
    } catch (ex: Exception) {
      Log.e("YkisLog", "deleteUserAccount: ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage))
    }
  }.flowOn(Dispatchers.IO)
}
