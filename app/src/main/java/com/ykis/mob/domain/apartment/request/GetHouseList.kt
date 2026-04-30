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
  // Теперь принимаем raionId как обязательный параметр
  operator fun invoke(raionId: Int): Flow<Resource<List<HouseEntity>>> = flow {
    val methodName = "UseCase.GetHouseList"
    try {
      emit(Resource.Loading())

      // 1. ПРОВЕРКА КЭША: Берем дома только выбранного района
      val localHouses = database.houseDao().getHousesByRaion(raionId)
      Log.d("YkisLog", "$methodName: [DB_READ] В кэше для района $raionId найдено: ${localHouses.size}")

      if (localHouses.isNotEmpty()) {
        emit(Resource.Success(localHouses))
      }

      // 2. СЕТЕВОЙ ЗАПРОС: Передаем raionId на сервер
      Log.d("YkisLog", "$methodName: [NETWORK_START] Запрос домов для района ID: $raionId")
      val response = repository.getHouseByRaionList(raionId)
      val remoteHouses = response.houses ?: emptyList()

      Log.d("YkisLog", "$methodName: [NETWORK_RESULT] Распарсено: ${remoteHouses.size}")

      if (remoteHouses.isNotEmpty()) {
        // 3. ОБНОВЛЕНИЕ БАЗЫ
        // Важно: прошиваем raionId каждому дому перед вставкой, если сервер его не прислал
        val housesWithRaion = remoteHouses.map { it.copy(raionId = raionId) }

        database.houseDao().insertHouseList(housesWithRaion)

        val verifyDb = database.houseDao().getHousesByRaion(raionId)
        Log.d("YkisLog", "$methodName: [DB_VERIFY] В базе теперь: ${verifyDb.size} домов этого района")

        emit(Resource.Success(verifyDb))
      } else {
        if (localHouses.isEmpty()) emit(Resource.Success(emptyList()))
      }

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      val fallback = database.houseDao().getHousesByRaion(raionId)
      emit(if (fallback.isNotEmpty()) Resource.Success(fallback) else Resource.Error(e.localizedMessage))
    }
  }.flowOn(Dispatchers.IO)
}








