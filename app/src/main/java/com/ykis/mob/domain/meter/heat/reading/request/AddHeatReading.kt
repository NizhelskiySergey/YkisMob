package com.ykis.mob.domain.meter.heat.reading.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import java.io.IOException

class AddHeatReading (
  private val repository: HeatMeterRepository,
  private val database: AppDatabase
) {
  // Указываем тип в flow<...>, чтобы избежать ошибок компиляции
  operator fun invoke(addReadingParams: AddHeatReadingParams): Flow<Resource<BaseResponse?>> = flow<Resource<BaseResponse?>> {
    try {
      emit(Resource.Loading())
      val response = repository.addHeatReading(addReadingParams)
      if(response.success == 1){
        emit(Resource.Success(response))
        // Если захотите кэшировать новое показание, вызов БД здесь будет безопасен
      }
    } catch (e: ResponseException) {
      // Ktor выбрасывает это при ошибках 4xx, 5xx и т.д.
      SnackbarManager.showMessage(e.response.status.description)
      emit(Resource.Error())
    } catch (e: IOException) {
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage))
    }
  }.flowOn(Dispatchers.IO) // Лучше добавить сразу для стабильности
}

