package com.ykis.mob.domain.apartment.request
import android.util.Log
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import com.ykis.mob.domain.apartment.HouseEntity
import com.ykis.mob.domain.apartment.RaionEntity
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class GetHouseList(
  private val repository: ApartmentRepository,
  private val database: AppDatabase
) {
  operator fun invoke(uid: String): Flow<Resource<List<HouseEntity>>> = flow {
    val methodName = "UseCase.GetHouseList"
    try {
      emit(Resource.Loading())

      // 1. ПРОВЕРКА КЭША
      val localHouses = database.houseDao().getHouseList()
      Log.d("YkisLog", "$methodName: [DB_READ] Найдено в базе: ${localHouses.size} шт.")

      if (localHouses.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [LOCAL_EMIT] Отправляем кэш в UI")
        emit(Resource.Success(localHouses))
      }

      // 2. СЕТЕВОЙ ЗАПРОС
      Log.d("YkisLog", "$methodName: [NETWORK_START] Запрос для UID: $uid")
      val response = repository.getHouseList(houseId)

      // Логируем сырой ответ (если возможно) или количество распарсенных объектов
      val remoteHouses = response.houses ?: emptyList()
      Log.d("YkisLog", "$methodName: [NETWORK_RESULT] Успех: ${response.success}, Сообщение: ${response.message}, Объектов распарсено: ${remoteHouses.size}")

      if (remoteHouses.isNotEmpty()) {
        // 3. ОБНОВЛЕНИЕ БАЗЫ
        Log.d("YkisLog", "$methodName: [DB_WRITE] Попытка вставки ${remoteHouses.size} записей в Room...")
        database.raionDao().insertRaionList(remoteHouses)

        // СРАЗУ ПРОВЕРЯЕМ ЗАПИСЬ
        val verifyDb = database.houseDao().getHouseList()
        Log.d("YkisLog", "$methodName: [DB_VERIFY] После записи в Room стало: ${verifyDb.size} строк")

        if (verifyDb.isEmpty()) {
          Log.e("YkisLog", "$methodName: [CRITICAL] Вставка была вызвана, но база пуста! Проверьте типы данных в RaionEntity.")
        }

        emit(Resource.Success(verifyDb))
      } else {
        Log.w("YkisLog", "$methodName: [EMPTY_RESULT] Сеть вернула 0 районов. Success в UI.")
        if (localHouses.isEmpty()) emit(Resource.Success(emptyList()))
      }

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] Тип: ${e.javaClass.simpleName}, Сообщение: ${e.message}")

      val fallback = database.houseDao().getHouseList()
      if (fallback.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [ERROR_FALLBACK] Выдаем старые данные из кэша")
        emit(Resource.Success(fallback))
      } else {
        emit(Resource.Error(e.localizedMessage ?: "Помилка завантаження районів"))
      }
    }
  }.flowOn(Dispatchers.IO)
}







