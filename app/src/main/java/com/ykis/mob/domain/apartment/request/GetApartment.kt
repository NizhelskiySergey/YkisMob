package com.ykis.mob.domain.apartment.request

import android.util.Log
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

class GetApartment(
  private val repository: ApartmentRepository,
  private val database: AppDatabase
) {
  operator fun invoke(addressId: Int, uid: String): Flow<Resource<ApartmentEntity>> = flow {
    val methodName = "UseCase.GetApartment"
    try {
      emit(Resource.Loading())

      // 1. ПРОВЕРКА ЛОКАЛЬНОЙ БАЗЫ
      val localApartment = database.apartmentDao().getFlatById(addressId = addressId)
      if (localApartment != null) {
        Log.d("YkisLog", "$methodName: [LOCAL] Найдена квартира $addressId")
        emit(Resource.Success(localApartment))
      }

      // 2. ЗАПРОС В СЕТЬ
      Log.d("YkisLog", "$methodName: [NETWORK] Запрос ID: $addressId")
      val response = repository.getApartment(addressId, uid)

      if (response.success == 1) {
        response.apartment?.let { remoteApartment ->

          // --- КРИТИЧЕСКИЙ ФИКС: Прошиваем UID перед записью в Room ---
          // Это гарантирует, что в Database Inspector поле uid больше не будет null
          val apartmentWithUid = remoteApartment.copy(uid = uid)

          database.apartmentDao().insertApartmentList(listOf(apartmentWithUid))
          Log.d("YkisLog", "$methodName: [DB_WRITE] Сохранено с UID: $uid")

          emit(Resource.Success(apartmentWithUid))
        } ?: run {
          Log.e("YkisLog", "$methodName: [ERROR] Пустой объект в ответе")
          emit(Resource.Error("Дані квартири відсутні"))
        }
      } else {
        // Если сервер вернул успех 0
        emit(Resource.Error("Помилка сервера: ${response.success}"))
      }

    } catch (e: ExceptionWithResourceMessage) {
      Log.e("YkisLog", "$methodName: [RESOURCE_ERROR] ${e.localizedMessage}")
      SnackbarManager.showMessage(e.resourceMessage)
      emit(Resource.Error(e.localizedMessage ?: "Error"))
    } catch (e: IOException) {
      Log.w("YkisLog", "$methodName: [OFFLINE] Проверка кэша")
      val cached = database.apartmentDao().getFlatById(addressId = addressId)
      if (cached != null) emit(Resource.Success(cached))
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error("Немає зв'язку"))
    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error"))
    }
  }.flowOn(Dispatchers.IO)
}

