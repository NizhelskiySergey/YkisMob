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
import kotlinx.io.IOException

class GetApartmentList(
  private val repository: ApartmentRepository,
  private val database: AppDatabase
) {
  operator fun invoke(uid: String): Flow<Resource<List<ApartmentEntity>>> = flow {
    val methodName = "UseCase.GetApartmentList"

    if (uid.isBlank()) {
      Log.e("YkisLog", "$methodName: [ABORT] UID пустой")
      emit(Resource.Error("Помилка авторизації"))
      return@flow
    }

    try {
      emit(Resource.Loading())

      // 1. ПРОВЕРКА ЛОКАЛЬНОЙ БАЗЫ (Мгновенный результат для UI)
      val localList = database.apartmentDao().getApartmentListByUid(uid)
      if (localList.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [LOCAL_HIT] Найдено ${localList.size} кв. в Room")
        emit(Resource.Success(localList))
      }

      // 2. ЗАПРОС В СЕТЬ (Обновление данных)
      Log.d("YkisLog", "$methodName: [NETWORK_START] Запрос для UID: $uid")
      val response = repository.getApartmentList(uid)
      val remoteApartments = response.apartments ?: emptyList()

      // Прошиваем UID каждой квартире для связки в БД
      val apartmentsWithUid = remoteApartments.map { it.copy(uid = uid) }

      // 3. АТОМАРНОЕ ОБНОВЛЕНИЕ БАЗЫ (Транзакция)
      database.withTransaction {
        val addressIds = apartmentsWithUid.map { it.addressId }

        // Очищаем старые данные только этого пользователя (или все, если логика требует)
        database.apartmentDao().deleteAllApartments()
        database.apartmentDao().insertApartmentList(apartmentsWithUid)

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
      Log.d("YkisLog", "$methodName: [NETWORK_SUCCESS] База синхронизирована (${apartmentsWithUid.size} кв.)")

      // Отдаем свежий список
      emit(Resource.Success(apartmentsWithUid))

    } catch (e: IOException) {
      // СЛУЧАЙ: СЕРВИС ЛЕЖИТ ИЛИ НЕТ СЕТИ
      Log.w("YkisLog", "$methodName: [OFFLINE_MODE] Сервис недоступен. Проверка кэша...")

      val fallbackList = database.apartmentDao().getApartmentListByUid(uid)
      if (fallbackList.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [OFFLINE_RECOVERY] Используем локальный список.")
        emit(Resource.Success(fallbackList))
        // Тихое уведомление, не блокирующее работу
        SnackbarManager.showMessage(R.string.error_network)
      } else {
        Log.e("YkisLog", "$methodName: [OFFLINE_FAIL] В базе пусто, сети нет.")
        emit(Resource.Error("Немає зв'язку. Список порожній."))
        SnackbarManager.showMessage(R.string.error_network)
      }

    } catch (e: Exception) {
      // КРИТИЧЕСКИЕ ОШИБКИ ЛОГИКИ
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      val fallbackList = database.apartmentDao().getApartmentListByUid(uid)

      if (fallbackList.isNotEmpty()) {
        emit(Resource.Success(fallbackList))
      } else {
        emit(Resource.Error(e.localizedMessage ?: "Помилка завантаження"))
      }
    }
  }.flowOn(Dispatchers.IO)
}





