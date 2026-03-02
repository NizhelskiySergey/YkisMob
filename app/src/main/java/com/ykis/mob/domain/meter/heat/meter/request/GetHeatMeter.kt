package com.ykis.mob.domain.meter.heat.meter.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.heat.meter.HeatMeterEntity
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import kotlinx.coroutines.Dispatchers // ДОБАВЛЕНО
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВЛЕНО
import retrofit2.HttpException
import java.io.IOException

class GetHeatMeterList (
  private val repository: HeatMeterRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<HeatMeterEntity>
  operator fun invoke(addressId: Int, uid: String): Flow<Resource<List<HeatMeterEntity>>> = flow {
    try {
      emit(Resource.Loading())

      // Сначала проверим локальную базу (опционально, для скорости)
      val localMeters = database.heatMeterDao().getHeatMeter(addressId)
      if (localMeters.isNotEmpty()) {
        emit(Resource.Success(localMeters))
      }

      val response = repository.getHeatMeterList(addressId, uid)

      if (response.success == 1) {
        val remoteMeters = response.heatMeters ?: emptyList()
        emit(Resource.Success(remoteMeters))
        database.heatMeterDao().insertHeatMeter(remoteMeters)
      }
    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "Error")
      emit(Resource.Error())
    } catch (e: IOException) {
      val meterList = database.heatMeterDao().getHeatMeter(addressId)
      if (meterList.isNotEmpty()) {
        emit(Resource.Success(meterList))
        return@flow
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error"))
    }
  }.flowOn(Dispatchers.IO) // <--- РЕШАЕТ ПРОБЛЕМУ ROOM CRASH
}

