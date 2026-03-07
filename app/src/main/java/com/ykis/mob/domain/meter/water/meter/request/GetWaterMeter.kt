package com.ykis.mob.domain.meter.water.meter.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.meter.water.meter.WaterMeterEntity
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import kotlinx.coroutines.Dispatchers // ОБЯЗАТЕЛЬНО
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ОБЯЗАТЕЛЬНО
import retrofit2.HttpException
import java.io.IOException

class GetWaterMeterList (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<WaterMeterEntity>, чтобы типы совпадали
  operator fun invoke(uid: String,addressId: Int): Flow<Resource<List<WaterMeterEntity>>> = flow {
    try {
      emit(Resource.Loading())

      // Запрос в БД (теперь безопасно на Dispatchers.IO)
      val localMeters = database.waterMeterDao().getWaterMeter(addressId)
      emit(Resource.Success(localMeters))

      val response = repository.getWaterMeterList(uid,addressId)

      // Обновляем локальные данные, если есть свежие из сети
      val waterMeterList = database.waterMeterDao().getWaterMeter(addressId)
      if (waterMeterList.isNotEmpty()) {
        emit(Resource.Success(waterMeterList))
      }

      if (response.success == 1) {
        val remoteMeters = response.waterMeters ?: emptyList()
        emit(Resource.Success(remoteMeters))
        database.waterMeterDao().insertWaterMeter(remoteMeters)
      }

    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "Error")
      emit(Resource.Error())
    } catch (e: IOException) {
      val waterMeterList = database.waterMeterDao().getWaterMeter(addressId)
      if (waterMeterList.isNotEmpty()) {
        emit(Resource.Success(waterMeterList))
        return@flow
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error"))
    }
  }.flowOn(Dispatchers.IO) // <--- РЕШАЕТ ПРОБЛЕМУ С ПОТОКАМИ (ROOM CRASH)
}

