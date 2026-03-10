package com.ykis.mob.domain.meter.water.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import java.io.IOException

class GetWaterReadings (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<WaterReadingEntity>, чтобы типы внутри flow совпадали
  operator fun invoke(uid: String, vodomerId: Int): Flow<Resource<List<WaterReadingEntity>>> = flow {
    try {
      emit(Resource.Loading())

      // 1. Показываем локальные данные из Room
      val localReadings = database.waterReadingDao().getWaterReadings(vodomerId)
      if (localReadings.isNotEmpty()) {
        emit(Resource.Success(localReadings))
      }

      // 2. Запрос в сеть через Ktor (уже без .await())
      val response = repository.getWaterReadings(uid, vodomerId)

      if (response.success == 1) {
        // В Ktor-модели мы настроили это поле как не-nullable со значением по умолчанию
        val remoteReadings = response.waterReadings

        emit(Resource.Success(remoteReadings))

        // 3. Сохранение в базу (на IO)
        // Теперь это гарантированно List<WaterReadingEntity>, а не List<WaterReadingEntity?>
        database.waterReadingDao().insertWaterReading(remoteReadings)
      } else {
        emit(Resource.Error(response.message))
      }

    } catch (e: ResponseException) {
      // Ошибки сервера (404, 500)
      SnackbarManager.showMessage("Ошибка сервера: ${e.response.status.value}")
      emit(Resource.Error())
    } catch (e: IOException) {
      // Ошибки сети (нет интернета) - показываем кэш
      val readingList = database.waterReadingDao().getWaterReadings(vodomerId)
      if (readingList.isNotEmpty()) {
        emit(Resource.Success(readingList))
      } else {
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      }
    } catch (e: Exception) {
      // Ошибки парсинга JSON или другие исключения
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // Гарантирует выполнение в фоновом потоке

}
