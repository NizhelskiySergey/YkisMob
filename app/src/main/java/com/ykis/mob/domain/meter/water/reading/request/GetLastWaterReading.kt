package com.ykis.mob.domain.meter.water.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

class GetLastWaterReading (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  operator fun invoke(uid: String, vodomerId: Int): Flow<Resource<WaterReadingEntity?>> =
    flow<Resource<WaterReadingEntity?>> { // Явно типизируем поток

      try {
        emit(Resource.Loading())

        // 1. Показываем последний кэш из базы (на Dispatchers.IO)
        val localReadings = database.waterReadingDao().getWaterReadings(vodomerId)
        if (localReadings.isNotEmpty()) {
          // Явно указываем тип <WaterReadingEntity?> для Success
          emit(Resource.Success<WaterReadingEntity?>(localReadings.lastOrNull()))
        }

        // 2. Запрос в сеть через Ktor (уже без .await())
        val response = repository.getLastWaterReading(uid, vodomerId)

        if (response.success == 1) {
          val reading = response.waterReading // Тип из модели: WaterReadingEntity?

          // Явно указываем тип <WaterReadingEntity?> для Success
          emit(Resource.Success<WaterReadingEntity?>(reading))

          // 3. Сохранение в базу через let (защита от null)
          reading?.let {
            database.waterReadingDao().insertWaterReading(listOf(it))
          }
        } else {
          emit(Resource.Error(response.message))
        }
      } catch (e: ResponseException) {
        // Ошибки сервера (например, 404 или 500)
        SnackbarManager.showMessage("Ошибка сервера: ${e.response.status.value}")
        emit(Resource.Error())
      } catch (e: IOException) {
        // Ошибка сети — показываем только кэш, если он есть
        val cachedReadings = database.waterReadingDao().getWaterReadings(vodomerId)
        if (cachedReadings.isNotEmpty()) {
          emit(Resource.Success<WaterReadingEntity?>(cachedReadings.lastOrNull()))
        }
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      } catch (e: Exception) {
        emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
      }
    }.flowOn(Dispatchers.IO) // <--- Решает проблему IllegalStateException и фризов UI
}
