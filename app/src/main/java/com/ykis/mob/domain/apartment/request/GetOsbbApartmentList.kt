package com.ykis.mob.domain.apartment.request
import android.util.Log
import androidx.room.withTransaction
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class GetOsbbApartmentsList(
  private val repository: ApartmentRepository,
  private val database: AppDatabase
) {
  operator fun invoke(targetId: Int, isHouseSearch: Boolean = false): Flow<Resource<List<ApartmentEntity>>> = flow {
    val type = if (isHouseSearch) "HOUSE" else "OSBB"
    val methodName = "UseCase.GetOsbbApartmentsList[$type]"

    try {
      Log.d("YkisLog", "$methodName: [START] Запрос всех квартир ID: $targetId")
      emit(Resource.Loading())

      // 1. ПРОВЕРКА КЭША (Админ сразу видит список, пока идет запрос в сеть)
      val localList = database.apartmentDao().getApartmentList()
      if (localList.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [LOCAL_HIT] Найдено ${localList.size} квартир в базе")
        emit(Resource.Success(localList))
      }

      // 2. ЗАПРОС В СЕТЬ
      val response = repository.getOsbbApartmentsList(targetId, isHouseSearch)
      val remoteApartments = response.apartments ?: emptyList()

      // 3. АТОМАРНОЕ ОБНОВЛЕНИЕ БАЗЫ
      if (remoteApartments.isNotEmpty()) {
        database.withTransaction {
          val addressIdList = remoteApartments.map { it.addressId }

          // Полная замена списка (для админа это актуально, чтобы убрать выписанных)
          database.apartmentDao().deleteAllApartments()
          database.apartmentDao().insertApartmentList(remoteApartments)

          // Чистим связанные хвосты
          database.familyDao().deleteFamilyByApartment(addressIdList)
          database.serviceDao().deleteServiceByApartment(addressIdList)
          database.paymentDao().deletePaymentByApartment(addressIdList)
          database.waterMeterDao().deleteWaterMeterByApartment(addressIdList)
          database.heatMeterDao().deleteHeatMeterByApartment(addressIdList)
          database.heatReadingDao().deleteHeatReadingsByApartment(addressIdList)
          database.waterReadingDao().deleteWaterReadingByApartment(addressIdList)
        }
        Log.d("YkisLog", "$methodName: [NETWORK_SUCCESS] База обновлена (${remoteApartments.size} кв.)")
        emit(Resource.Success(remoteApartments))
      } else {
        Log.w("YkisLog", "$methodName: [NETWORK_EMPTY] Сервер вернул пустой список")
        // Если сервер вернул пусто, но у нас в базе что-то было — не затираем,
        // просто оставляем старый успех.
      }

    } catch (e: java.io.IOException) {
      // КЕЙС: СЕРВИС ЛЕЖИТ / НЕТ ИНТЕРНЕТА
      Log.w("YkisLog", "$methodName: [OFFLINE_MODE] Проверка резервного кэша...")
      val cached = database.apartmentDao().getApartmentList()

      if (cached.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [OFFLINE_RECOVERY] Работаем на локальных данных")
        emit(Resource.Success(cached))
        SnackbarManager.showMessage(R.string.error_network)
      } else {
        Log.e("YkisLog", "$methodName: [OFFLINE_FAIL] Сети нет и база пуста")
        emit(Resource.Error("Немає зв'язку. Список мешканців недоступний."))
        SnackbarManager.showMessage(R.string.error_network)
      }

    } catch (e: Exception) {
      // КРИТИЧЕСКИЕ ОШИБКИ
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      val fallback = database.apartmentDao().getApartmentList()
      if (fallback.isNotEmpty()) {
        emit(Resource.Success(fallback))
      } else {
        emit(Resource.Error(e.localizedMessage ?: "Помилка завантаження списку"))
      }
    }
  }.flowOn(Dispatchers.IO)
}





