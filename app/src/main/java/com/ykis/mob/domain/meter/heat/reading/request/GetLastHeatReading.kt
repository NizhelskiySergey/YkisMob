package com.ykis.mob.domain.meter.heat.reading.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import java.io.IOException

class GetLastHeatReading (
  private val repository: HeatMeterRepository,
  private val database: AppDatabase
) {
  // Указываем тип явно в flow<...>, чтобы не было ошибки mismatch
  operator fun invoke(uid: String, teplomerId: Int): Flow<Resource<HeatReadingEntity?>> =
    flow<Resource<HeatReadingEntity?>> { // Явно указываем тип здесь
      try {
        emit(Resource.Loading())

        // 1. Показываем данные из базы
        val localReadings = database.heatReadingDao().getHeatReading(teplomerId)
        if (localReadings.isNotEmpty()) {
          // Явно указываем <HeatReadingEntity?> для Success
          emit(Resource.Success<HeatReadingEntity?>(localReadings.lastOrNull()))
        }

        // 2. Запрос в сеть через Ktor
        val response = repository.getLastHeatReading(uid, teplomerId)

        if (response.success == 1) {
          val reading = response.heatReading // Тип из модели: HeatReadingEntity?

          // Явно указываем <HeatReadingEntity?> для Success
          emit(Resource.Success<HeatReadingEntity?>(reading))

          // 3. Сохранение в базу через let (безопасно)
          reading?.let {
            database.heatReadingDao().insertHeatReading(listOf(it))
          }
        } else {
          emit(Resource.Error(response.message))
        }
      } catch (e: ResponseException) {
        SnackbarManager.showMessage("Ошибка сервера: ${e.response.status.value}")
        emit(Resource.Error())
      } catch (e: IOException) {
        // В случае ошибки сети показываем только кэш
        val lastReadings = database.heatReadingDao().getHeatReading(teplomerId)
        if (lastReadings.isNotEmpty()) {
          emit(Resource.Success<HeatReadingEntity?>(lastReadings.lastOrNull()))
        }
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      } catch (e: Exception) {
        emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
      }
    }.flowOn(Dispatchers.IO)

}

