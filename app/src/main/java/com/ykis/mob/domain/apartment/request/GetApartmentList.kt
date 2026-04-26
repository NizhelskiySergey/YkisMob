package com.ykis.mob.domain.apartment.request
import android.util.Log
import androidx.room.withTransaction
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class GetApartmentList(
  private val repository: ApartmentRepository,
  private val database: AppDatabase
) {
  operator fun invoke(
    uid: String
  ): Flow<Resource<List<ApartmentEntity>>> = flow {
    val methodName = "UseCase.GetApartmentList"

    // Защита от пустого UID
    if (uid.isBlank()) {
      Log.e("YkisLog", "$methodName: [ABORT] UID пустой")
      emit(Resource.Error("Ошибка авторизации"))
      return@flow
    }

    try {
      emit(Resource.Loading())

      // 1. Сначала отдаем локальный кэш (фильтруем по UID!)
      val localList = database.apartmentDao().getApartmentListByUid(uid)
      if (localList.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [LOCAL] Найдено ${localList.size} кв. для $uid")
        emit(Resource.Success(localList))
      }

      // 2. Запрос в сеть
      Log.d("YkisLog", "$methodName: [NETWORK] Запрос для UID: $uid")
      val response = repository.getApartmentList(uid)
      val remoteApartments = response.apartments ?: emptyList()

      // --- КРИТИЧЕСКИЙ ФИКС: Прошиваем UID каждой квартире ---
      // Это убирает null в Database Inspector и связывает данные с юзером
      val apartmentsWithUid = remoteApartments.map { it.copy(uid = uid) }

      // 3. АТОМАРНОЕ ОБНОВЛЕНИЕ БАЗЫ
      database.withTransaction {
        val addressIds = apartmentsWithUid.map { it.addressId }

        // Чистим старое (можно только этого юзера, если нужно) и пишем новое
        database.apartmentDao().deleteAllApartments()
        database.apartmentDao().insertApartmentList(apartmentsWithUid)

        // Чистим связанные таблицы по списку полученных ID
        if (addressIds.isNotEmpty()) {
          database.familyDao().deleteFamilyByApartment(addressIds)
          database.serviceDao().deleteServiceByApartment(addressIds)
          database.paymentDao().deletePaymentByApartment(addressIds)
          database.waterMeterDao().deleteWaterMeterByApartment(addressIds)
          database.heatMeterDao().deleteHeatMeterByApartment(addressIds)
          database.heatReadingDao().deleteHeatReadingsByApartment(addressIds)
          database.waterReadingDao().deleteWaterReadingByApartment(addressIds)
        }
      }

      Log.d("YkisLog", "$methodName: [DB_WRITE] Успешно сохранено ${apartmentsWithUid.size} кв.")

      // 4. Отдаем финальный результат в UI
      emit(Resource.Success(apartmentsWithUid))

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      val fallbackList = database.apartmentDao().getApartmentListByUid(uid)
      if (fallbackList.isNotEmpty()) {
        emit(Resource.Success(fallbackList))
      } else {
        emit(Resource.Error(e.localizedMessage ?: "Помилка завантаження"))
      }
    }
  }.flowOn(Dispatchers.IO)
}




