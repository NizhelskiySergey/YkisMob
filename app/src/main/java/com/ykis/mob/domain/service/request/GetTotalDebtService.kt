package com.ykis.mob.domain.service.request
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

class GetTotalDebtServices (
  private val repository: ServiceRepository,
  private val database : AppDatabase
){
  operator fun invoke (params:ServiceParams) : Flow<Resource<ServiceEntity>> = flow {
    try {
      emit(Resource.Loading())
      val response = repository.getTotalDebtService(ServiceParams(
        uid = params.uid,
        addressId = params.addressId,
        houseId = params.houseId,
        year = params.year,
        service = params.service,
        total = params.total,
      ))

      if (response.success == 1 && response.services.isNotEmpty()) {
        // Запись в базу теперь на IO потоке
        database.serviceDao().insertService(response.services)
        emit(Resource.Success(response.services[0]))
      } else {
        throw ExceptionWithResourceMessage(R.string.generic_error)
      }

    } catch (e: ResponseException) {
      // Ktor выбрасывает это при ошибках 4xx, 5xx и т.д.
      SnackbarManager.showMessage(e.response.status.description)
      emit(Resource.Error())
    } catch (e: IOException) {
      emit(Resource.Error("Check your internet connection"))
      // Чтение из базы теперь на IO потоке
      val totalDebt = database.serviceDao().getTotalDebt(params.addressId)
      if (totalDebt != null) {
        emit(Resource.Success(totalDebt))
      }
    } catch (e: ExceptionWithResourceMessage) {
      SnackbarManager.showMessage(e.resourceMessage)
      emit(Resource.Error()) // Добавил emit, чтобы поток не "завис"
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // <--- ФИКС КРЭША
}
