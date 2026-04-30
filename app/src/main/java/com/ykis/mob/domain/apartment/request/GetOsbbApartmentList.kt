package com.ykis.mob.domain.apartment.request
import android.util.Log
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

  class GetOsbbApartmentsList(
    private val repository: ApartmentRepository,
    private val database: AppDatabase // БД остается в конструкторе для совместимости, но тут не используется
  ) {

    /**
     * ВЕРСИЯ ДЛЯ АДМИНА (по osbbId)
     * Загружает полный список жителей дома для отображения в Drawer.
     * Не кэширует данные в Room, чтобы избежать конфликтов.
     */
    operator fun invoke(targetId: Int,isHouseSearch: Boolean = false): Flow<Resource<List<ApartmentEntity>>> = flow {
      val type = if (isHouseSearch) "HOUSE" else "OSBB"
      val methodName = "UseCase.GetOsbbApartmentsList[$type]"
      val addressIdList = mutableListOf<Int>()
      try {
        Log.d("YkisLog", "$methodName: [START] Запрос всех квартир ОСББ ID: $targetId")
        emit(Resource.Loading())

        // Все эти вызовы БД теперь будут на IO потоке
        val localList = database.apartmentDao().getApartmentList()
        emit(Resource.Success(localList))

        val response = repository.getOsbbApartmentsList(targetId,isHouseSearch)

        database.apartmentDao().deleteAllApartments()
        database.apartmentDao().insertApartmentList(response.apartments)

        for (i in response.apartments) {
          addressIdList.add(i.addressId)
        }

        database.familyDao().deleteFamilyByApartment(addressIdList)
        database.serviceDao().deleteServiceByApartment(addressIdList)
        database.paymentDao().deletePaymentByApartment(addressIdList)
        database.waterMeterDao().deleteWaterMeterByApartment(addressIdList)
        database.heatMeterDao().deleteHeatMeterByApartment(addressIdList)
        database.heatReadingDao().deleteHeatReadingsByApartment(addressIdList)
        database.waterReadingDao().deleteWaterReadingByApartment(addressIdList)

        addressIdList.clear()
        emit(Resource.Success(response.apartments))

      } catch (e: Exception) {
        Log.e("YkisLog", "$methodName: [ERROR] Ошибка загрузки: ${e.localizedMessage}")
        emit(Resource.Error(e.localizedMessage ?: "Unexpected error!"))

      } catch (e: Exception) {
        val apartmentList = database.apartmentDao().getApartmentList()
        if (apartmentList.isNotEmpty()) {
          Log.d("YkisLog", "$methodName: [SUCCESS] Получено ${apartmentList.size} квартир")
          emit(Resource.Success(apartmentList))
          return@flow
        }
        emit(Resource.Error("Check your internet connection"))
      }
    }.flowOn(Dispatchers.IO) // <--- ЭТО РЕШАЕТ ПРОБЛЕМУ CRASH
  }




