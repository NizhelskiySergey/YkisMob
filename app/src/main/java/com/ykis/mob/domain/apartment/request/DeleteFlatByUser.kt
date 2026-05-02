package com.ykis.mob.domain.apartment.request
import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import java.io.IOException

class DeleteApartment(
  private val repository: ApartmentRepository,
  private val appDatabase: AppDatabase
) {
  operator fun invoke(addressId: Int, uid: String): Flow<Resource<BaseResponse>> = flow {
    val methodName = "UseCase.DeleteApartment"

    try {
      Log.d("YkisLog", "$methodName: [START] Удаление ID: $addressId для UID: $uid")
      emit(Resource.Loading())

      // 1. ЗАПРОС В СЕТЬ
      val response = repository.deleteApartment(addressId, uid)

      Log.d("YkisLog", "$methodName: [RESPONSE] Success: ${response.success}, Message: ${response.message}")

      if (response.success == 1) {
        // 2. ОЧИСТКА ЛОКАЛЬНОЙ БАЗЫ (Room)
        // Выполняем только после подтверждения от сервера
        appDatabase.apartmentDao().deleteFlat(addressId)
        Log.d("YkisLog", "$methodName: [DB_CLEAN] Квартира $addressId удалена из локального кэша")

        emit(Resource.Success(response))
      } else {
        Log.e("YkisLog", "$methodName: [SERVER_REJECT] Ошибка удаления: ${response.message}")
        throw ExceptionWithResourceMessage(R.string.error_delete_flat)
      }

    } catch (e: java.io.IOException) {
      // СЛУЧАЙ: ВАШ СЕРВИС ЛЕЖИТ / НЕТ СЕТИ
      Log.e("YkisLog", "$methodName: [NETWORK_FAIL] Ошибка связи (IOException)")
      // Используем специальный ресурс для ошибки сети при удалении
      emit(Resource.Error(resourceMessage = R.string.error_network_delete))

    } catch (e: ExceptionWithResourceMessage) {
      Log.w("YkisLog", "$methodName: [LOGIC_ERROR] Сервер вернул успех 0")
      emit(Resource.Error(resourceMessage = e.resourceMessage))

    } catch (ex: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL] Непредвиденная ошибка: ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage ?: "Непередбачена помилка"))
    }
  }.flowOn(Dispatchers.IO)
}



