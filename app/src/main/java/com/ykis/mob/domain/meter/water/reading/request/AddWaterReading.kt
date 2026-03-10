package com.ykis.mob.domain.meter.water.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.GetSimpleResponse // ИСПОЛЬЗУЕМ КОНКРЕТНЫЙ КЛАСС
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

class AddWaterReading (
  private val repository: WaterMeterRepository,
  private val database: AppDatabase
) {
  // Указываем конкретный тип GetSimpleResponse? для Flow и Success
  operator fun invoke(addReadingParams: AddWaterReadingParams): Flow<Resource<GetSimpleResponse?>> =
    flow<Resource<GetSimpleResponse?>> {

      try {
        emit(Resource.Loading())

        // В репозитории метод теперь тоже должен возвращать GetSimpleResponse
        val response = repository.addWaterReading(addReadingParams)

        if (response.success == 1) {
          // Явно типизируем Success для устранения mismatch
          emit(Resource.Success<GetSimpleResponse?>(response))
          SnackbarManager.showMessage(R.string.reading_added)
        } else {
          // Показываем сообщение об ошибке от сервера (PHP)
          emit(Resource.Error(response.message))
          SnackbarManager.showMessage(response.message)
        }
      } catch (e: ResponseException) {
        // Ошибки Ktor (например, 403 или 500)
        SnackbarManager.showMessage("Ошибка сервера: ${e.response.status.value}")
        emit(Resource.Error())
      } catch (e: IOException) {
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      } catch (e: Exception) {
        // Перехватит SerializationException, если класс не помечен @Serializable
        emit(Resource.Error(e.localizedMessage ?: "Unknown error"))
      }
    }.flowOn(Dispatchers.IO)
}
