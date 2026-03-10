package com.ykis.mob.domain.meter.water.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers // НУЖНО ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // НУЖНО ДОБАВИТЬ
import java.io.IOException

class GetLastWaterReading (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  operator fun invoke(uid: String,vodomerId: Int): Flow<Resource<WaterReadingEntity?>> = flow<Resource<WaterReadingEntity?>> {

  try {
      emit(Resource.Loading())

      // 1. Сначала пробуем взять из базы (безопасно на IO)
      val lastReading = database.waterReadingDao().getWaterReadings(vodomerId)
      if (lastReading.isNotEmpty()) {
        emit(Resource.Success(lastReading.lastOrNull()))
      }

      // 2. Идем в сеть
      val response = repository.getLastWaterReading(uid,vodomerId)

      if (response.success == 1) {
        val reading = response.waterReading
        emit(Resource.Success(reading))

        // 3. Сохраняем в базу (если не null)
        reading?.let {
          database.waterReadingDao().insertWaterReading(listOf(it))
        }
      }
  } catch (e: ResponseException) {
    // Ktor выбрасывает это при ошибках 4xx, 5xx и т.д.
    SnackbarManager.showMessage(e.response.status.description)
    emit(Resource.Error())
  } catch (e: IOException) {
      val lastReading = database.waterReadingDao().getWaterReadings(vodomerId)
      if (lastReading.isNotEmpty()) {
        emit(Resource.Success(lastReading.lastOrNull()))
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      // Общий перехват, чтобы не было красного подчеркивания из-за необработанных ошибок
      emit(Resource.Error(e.localizedMessage))
    }
  }.flowOn(Dispatchers.IO) // <--- ЭТО УБИРАЕТ ILLEGALSTATEEXCEPTION
}
