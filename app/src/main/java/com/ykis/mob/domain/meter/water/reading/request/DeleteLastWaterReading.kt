package com.ykis.mob.domain.meter.water.reading.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import java.io.IOException

class DeleteLastWaterReading (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  // Указываем тип в flow<...>, чтобы не было ошибки mismatch из-за BaseResponse?
  operator fun invoke(uid: String,readingId : Int ): Flow<Resource<BaseResponse?>> = flow<Resource<BaseResponse?>> {
    try {
      emit(Resource.Loading())
      val response = repository.deleteLastWaterReading(uid,readingId)

      if(response.success == 1){
        // Удаление из базы теперь на IO потоке
        database.waterReadingDao().deleteWaterReadingById(readingId)
        emit(Resource.Success(response))
      }
    } catch (e: ResponseException) {
      // Ktor выбрасывает это при ошибках 4xx, 5xx и т.д.
      SnackbarManager.showMessage(e.response.status.description)
      emit(Resource.Error())
    } catch (e: IOException) {
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage))
    }
  }.flowOn(Dispatchers.IO) // <--- ФИКС ДЛЯ ROOM
}

