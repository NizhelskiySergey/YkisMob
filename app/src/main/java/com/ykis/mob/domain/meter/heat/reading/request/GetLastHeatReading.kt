package com.ykis.mob.domain.meter.heat.reading.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity
import com.ykis.mob.domain.meter.heat.reading.HeatReadingRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import retrofit2.HttpException
import java.io.IOException

class GetLastHeatReading (
  private val repository: HeatReadingRepository,
  private val database: AppDatabase
) {
  // Указываем тип явно в flow<...>, чтобы не было ошибки mismatch
  operator fun invoke(teplomerId: Int, uid: String): Flow<Resource<HeatReadingEntity?>> = flow<Resource<HeatReadingEntity?>> {
    try {
      emit(Resource.Loading())

      // 1. Показываем данные из базы (безопасно на IO)
      val localReadings = database.heatReadingDao().getHeatReading(teplomerId)
      if (localReadings.isNotEmpty()) {
        emit(Resource.Success(localReadings.lastOrNull()))
      }

      // 2. Запрос в сеть
      val response = repository.getLastHeatReading(teplomerId, uid)

      if (response.success == 1) {
        val reading = response.heatReading
        emit(Resource.Success(reading))

        // 3. Сохранение в базу
        reading?.let {
          database.heatReadingDao().insertHeatReading(listOf(it))
        }
      }
    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "Error")
      emit(Resource.Error())
    } catch (e: IOException) {
      val lastReading = database.heatReadingDao().getHeatReading(teplomerId)
      emit(Resource.Success(lastReading.lastOrNull()))
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage))
    }
  }.flowOn(Dispatchers.IO) // <--- РЕШАЕТ ПРОБЛЕМУ ILLEGALSTATEEXCEPTION
}

