package com.ykis.mob.domain.service.request
import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.service.ServiceEntity
import com.ykis.mob.domain.service.ServiceRepository
import com.ykis.mob.domain.service.request.ServiceParams // Проверь импорт ServiceParams
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import java.io.IOException

class GetTotalDebtServices(
  private val repository: ServiceRepository,
  private val database: AppDatabase
) {
  operator fun invoke(params: ServiceParams): Flow<Resource<ServiceEntity>> = flow {
    val methodName = "GetTotalDebt"
    try {
      Log.d("YkisLog", "$methodName: [START] AddrID: ${params.addressId}, UID: ${params.uid}")
      emit(Resource.Loading())

      // 1. ЗАПРОС В СЕТЬ
      val response = repository.getTotalDebtService(
        ServiceParams(
          uid = params.uid,
          addressId = params.addressId,
          houseId = params.houseId,
          year = params.year,
          service = params.service,
          total = params.total,
        )
      )

      if (response.success == 1 && response.services.isNotEmpty()) {
        val serviceData = response.services[0]
        Log.d("YkisLog", "$methodName: [NETWORK_SUCCESS] Debt: ${serviceData.dolg}")

        // Запись в базу
        database.serviceDao().insertService(response.services)
        emit(Resource.Success(serviceData))
      } else {
        Log.w("YkisLog", "$methodName: [SERVER_REJECT] Success=0 или список пуст")
        // Пытаемся взять из базы, если сеть ответила отказом
        val totalDebt = database.serviceDao().getTotalDebt(params.addressId)
        if (totalDebt != null) {
          Log.d("YkisLog", "$methodName: [DB_FALLBACK] Найдено в базе после отказа сети")
          emit(Resource.Success(totalDebt))
        } else {
          emit(Resource.Error(message = "Дані відсутні"))
        }
      }

    } catch (e: ResponseException) {
      Log.e("YkisLog", "$methodName: [HTTP_ERROR] ${e.response.status}")
      SnackbarManager.showMessage(e.response.status.description)
      emit(Resource.Error())
    } catch (e: IOException) {
      Log.e("YkisLog", "$methodName: [IO_ERROR] Ошибка сети")
      // Чтение из базы при отсутствии интернета
      val totalDebt = database.serviceDao().getTotalDebt(params.addressId)
      if (totalDebt != null) {
        Log.d("YkisLog", "$methodName: [OFFLINE_MODE] Данные из Room")
        emit(Resource.Success(totalDebt))
      } else {
        emit(Resource.Error("Перевірте підключення до інтернету"))
      }
    } catch (e: ExceptionWithResourceMessage) {
      Log.e("YkisLog", "$methodName: [RESOURCE_EXCEPTION]")
      SnackbarManager.showMessage(e.resourceMessage)
      emit(Resource.Error())
    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [CRITICAL] ${e.message}")
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO)
}

