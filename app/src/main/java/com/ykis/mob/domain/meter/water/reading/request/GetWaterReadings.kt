package com.ykis.mob.domain.meter.water.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import retrofit2.HttpException
import java.io.IOException

class GetWaterReadings (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<WaterReadingEntity>, чтобы типы внутри flow совпадали
  operator fun invoke(uid: String,vodomerId: Int): Flow<Resource<List<WaterReadingEntity>>> = flow {
    try {
      emit(Resource.Loading())

      // 1. Показываем локальные данные (теперь на IO)
      val localReadings = database.waterReadingDao().getWaterReadings(vodomerId)
      if (localReadings.isNotEmpty()) {
        emit(Resource.Success(localReadings))
      }

      // 2. Запрос в сеть
      val response = repository.getWaterReadings(uid,vodomerId)

      if (response.success == 1) {
        val remoteReadings = response.waterReadings ?: emptyList()
        emit(Resource.Success(remoteReadings))

        // 3. Сохранение в базу (на IO)
        database.waterReadingDao().insertWaterReading(remoteReadings)
      }
    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "Error")
      emit(Resource.Error())
    } catch (e: IOException) {
      val readingList = database.waterReadingDao().getWaterReadings(vodomerId)
      if (readingList.isNotEmpty()) {
        emit(Resource.Success(readingList))
        return@flow
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // <--- ФИКС CRASH ROOM
}
