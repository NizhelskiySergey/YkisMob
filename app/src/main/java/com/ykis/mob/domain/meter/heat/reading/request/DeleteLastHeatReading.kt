package com.ykis.mob.domain.meter.heat.reading.request

import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.GetSimpleResponse // ИСПОЛЬЗУЕМ КОНКРЕТНЫЙ КЛАСС
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

class DeleteLastHeatReading (
  private val repository: HeatMeterRepository,
  private val database: AppDatabase
) {
  // Указываем конкретный класс ответа вместо интерфейса
  operator fun invoke(uid: String, readingId: Int): Flow<Resource<GetSimpleResponse?>> =
    flow<Resource<GetSimpleResponse?>> {
      try {
        emit(Resource.Loading())

        // В репозитории метод тоже должен возвращать GetSimpleResponse
        val response = repository.deleteLastHeatReading(uid, readingId)

        if (response.success == 1) {
          // Удаление из Room на Dispatchers.IO
          database.heatReadingDao().deleteHeatReadingById(readingId)

          // Явно указываем nullable тип в Success
          emit(Resource.Success<GetSimpleResponse?>(response))
          SnackbarManager.showMessage(R.string.success_delete)
        } else {
          emit(Resource.Error(response.message))
        }
      } catch (e: ResponseException) {
        SnackbarManager.showMessage("Ошибка сервера: ${e.response.status.value}")
        emit(Resource.Error())
      } catch (e: IOException) {
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      } catch (e: Exception) {
        // Это перехватит SerializationException, если класс не помечен @Serializable
        emit(Resource.Error(e.localizedMessage ?: "Unknown error"))
      }
    }.flowOn(Dispatchers.IO)
}
