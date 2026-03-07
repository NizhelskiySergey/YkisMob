package com.ykis.mob.domain.meter.water.reading.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import retrofit2.HttpException
import java.io.IOException

class
AddWaterReading (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  // Указываем тип явно в flow<...>, чтобы компилятор не ругался на BaseResponse?
  operator fun invoke(addReadingParams: AddWaterReadingParams): Flow<Resource<BaseResponse?>> = flow<Resource<BaseResponse?>> {
    try {
      emit(Resource.Loading())
      val response = repository.addWaterReading(addReadingParams)
      if (response.success == 1) {
        emit(Resource.Success(response))
        // Если решите сохранять новое показание в базу после отправки,
        // с flowOn(Dispatchers.IO) это будет безопасно.
      }
    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "Error")
      emit(Resource.Error())
    } catch (e: IOException) {
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage))
    }
  }.flowOn(Dispatchers.IO) // Гарантирует стабильность
}

