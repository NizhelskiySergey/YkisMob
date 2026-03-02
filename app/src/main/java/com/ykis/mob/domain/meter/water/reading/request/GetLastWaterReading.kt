package com.ykis.mob.domain.meter.water.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity
import com.ykis.mob.domain.meter.water.reading.WaterReadingRepository
import kotlinx.coroutines.Dispatchers // НУЖНО ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // НУЖНО ДОБАВИТЬ
import retrofit2.HttpException
import java.io.IOException

class GetLastWaterReading (
  private val repository: WaterReadingRepository,
  private val database: AppDatabase
) {
  operator fun invoke(vodomerId: Int, uid: String): Flow<Resource<WaterReadingEntity?>> = flow<Resource<WaterReadingEntity?>> {

  try {
      emit(Resource.Loading())

      // 1. Сначала пробуем взять из базы (безопасно на IO)
      val lastReading = database.waterReadingDao().getWaterReadings(vodomerId)
      if (lastReading.isNotEmpty()) {
        emit(Resource.Success(lastReading.lastOrNull()))
      }

      // 2. Идем в сеть
      val response = repository.getLastWaterReading(vodomerId, uid)

      if (response.success == 1) {
        val reading = response.waterReading
        emit(Resource.Success(reading))

        // 3. Сохраняем в базу (если не null)
        reading?.let {
          database.waterReadingDao().insertWaterReading(listOf(it))
        }
      }
    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "Error")
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
