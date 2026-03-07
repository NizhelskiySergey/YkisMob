package com.ykis.mob.domain.meter.heat.reading.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import retrofit2.HttpException
import java.io.IOException

class GetHeatReadings (
  private val repository: HeatMeterRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<HeatReadingEntity>, чтобы типы внутри flow совпадали
  operator fun invoke(uid: String,teplomerId: Int): Flow<Resource<List<HeatReadingEntity>>> = flow {
    try {
      emit(Resource.Loading())

      // 1. Сначала пробуем показать данные из базы (теперь на IO)
      val readingList = database.heatReadingDao().getHeatReading(teplomerId)
      if (readingList.isNotEmpty()) {
        emit(Resource.Success(readingList))
      }

      // 2. Запрос в сеть
      val response = repository.getHeatReadings(uid,teplomerId)

      if (response.success == 1) {
        val remoteReadings = response.heatReadings ?: emptyList()
        emit(Resource.Success(remoteReadings))

        // 3. Синхронизация с базой (на IO)
        database.heatReadingDao().insertHeatReading(remoteReadings)
      }
    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "Error")
      emit(Resource.Error())
    } catch (e: IOException) {
      val readingList = database.heatReadingDao().getHeatReading(teplomerId)
      if (readingList.isNotEmpty()) {
        emit(Resource.Success(readingList))
        return@flow
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // <--- РЕШЕНИЕ CRASH ROOM
}

