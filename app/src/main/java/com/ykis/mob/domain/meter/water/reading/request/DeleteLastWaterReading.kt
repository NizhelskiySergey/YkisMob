package com.ykis.mob.domain.meter.water.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

class DeleteLastWaterReading (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  // Явно типизируем Flow и каждый emit
  operator fun invoke(uid: String, readingId: Int): Flow<Resource<BaseResponse?>> =
    flow<Resource<BaseResponse?>> { // 1. Указываем nullable тип здесь

      try {
        emit(Resource.Loading())

        val response = repository.deleteLastWaterReading(uid, readingId)

        if (response.success == 1) {
          // Удаление из Room на Dispatchers.IO
          database.waterReadingDao().deleteWaterReadingById(readingId)

          // 2. Явно указываем тип в Success, чтобы избежать mismatch
          emit(Resource.Success<BaseResponse?>(response))

          SnackbarManager.showMessage(R.string.success_delete)
        } else {
          emit(Resource.Error(response.message))
        }
      } catch (e: ResponseException) {
        SnackbarManager.showMessage("Ошибка: ${e.response.status.value}")
        emit(Resource.Error())
      } catch (e: IOException) {
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      } catch (e: Exception) {
        emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
      }
    }.flowOn(Dispatchers.IO) // 3. Гарантируем выполнение Room-операций в фоне
}
