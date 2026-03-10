package com.ykis.mob.domain.apartment.request

import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

class GetApartment (
  private val repository: ApartmentRepository,
  private val database: AppDatabase
){
  operator fun invoke(addressId: Int, uid: String): Flow<Resource<ApartmentEntity>> = flow {
    try {
      emit(Resource.Loading())

      // 1. Проверка локальной базы
      val localApartment = database.apartmentDao().getFlatById(addressId = addressId)
      if (localApartment != null) {
        emit(Resource.Success(localApartment))
      }

      // 2. Запрос в сеть через Ktor
      val response = repository.getApartment(addressId, uid)

      if (response.success == 1) {
        // РЕШЕНИЕ: Безопасно извлекаем объект из ответа
        response.apartment?.let { remoteApartment ->
          emit(Resource.Success(remoteApartment))
          // Теперь компилятор видит List<ApartmentEntity>, а не List<ApartmentEntity?>
          database.apartmentDao().insertApartmentList(listOf(remoteApartment))
        } ?: run {
          // Если успех 1, но объекта нет — это ошибка данных сервера
          emit(Resource.Error("Данные квартиры отсутствуют в ответе"))
        }
      } else {
        throw ExceptionWithResourceMessage(R.string.generic_error)
      }

    } catch (e: ExceptionWithResourceMessage) {
      SnackbarManager.showMessage(e.resourceMessage)
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error!"))
    } catch (e: IOException) {
      // 3. Обработка отсутствия сети
      val cachedApartment = database.apartmentDao().getFlatById(addressId = addressId)
      if (cachedApartment != null) {
        emit(Resource.Success(cachedApartment))
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error"))
    }
  }.flowOn(Dispatchers.IO)
}

