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

class GetHeatReadings (
  private val repository: HeatMeterRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<HeatReadingEntity>, чтобы типы внутри flow совпадали
  operator fun invoke(uid: String, teplomerId: Int): Flow<Resource<List<HeatReadingEntity>>> = flow {
    try {
      emit(Resource.Loading())

      // 1. Показываем локальный кэш из Room
      val localReadings = database.heatReadingDao().getHeatReading(teplomerId)
      if (localReadings.isNotEmpty()) {
        emit(Resource.Success(localReadings))
      }

      // 2. Запрос в сеть через Ktor (уже без .await())
      val response = repository.getHeatReadings(uid, teplomerId)

      if (response.success == 1) {
        // В Ktor-модели мы настроили это как List<HeatReadingEntity> = emptyList()
        // Поэтому 'remoteReadings' гарантированно не null.
        val remoteReadings = response.heatReadings

        emit(Resource.Success(remoteReadings))

        // 3. Сохранение в базу (выполняется на Dispatchers.IO)
        database.heatReadingDao().insertHeatReading(remoteReadings)
      } else {
        emit(Resource.Error(response.message))
      }

    } catch (e: ResponseException) {
      // Ошибки Ktor (например, 404, 500 или ошибка PHP) [1]
      SnackbarManager.showMessage("Ошибка сервера тепла: ${e.response.status.value}")
      emit(Resource.Error())
    } catch (e: IOException) {
      // Нет интернета — пробуем еще раз показать кэш [1]
      val cached = database.heatReadingDao().getHeatReading(teplomerId)
      if (cached.isNotEmpty()) {
        emit(Resource.Success(cached))
      } else {
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      }
    } catch (e: Exception) {
      // Ошибки парсинга JSON (SerializationException) или другие [1]
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // <--- Критично для Kotzilla и Room [1]

}

