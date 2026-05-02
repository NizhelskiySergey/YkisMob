package com.ykis.mob.domain.apartment.request
import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
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
  operator fun invoke(raionId: Int): Flow<Resource<List<HouseEntity>>> = flow {
    val methodName = "UseCase.GetHouseList"

    try {
      emit(Resource.Loading())

      // 1. ПРОВЕРКА КЭША (Мгновенное отображение)
      val localHouses = database.houseDao().getHousesByRaion(raionId)
      if (localHouses.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [LOCAL_HIT] Найдено ${localHouses.size} домов для района $raionId")
        emit(Resource.Success(localHouses))
      }

      // 2. ЗАПРОС В СЕТЬ
      Log.d("YkisLog", "$methodName: [NETWORK_START] Запрос домов для района ID: $raionId")
      val response = repository.getHouseByRaionList(raionId)
      val remoteHouses = response.houses ?: emptyList()

      // 3. АТОМАРНОЕ ОБНОВЛЕНИЕ БАЗЫ
      if (remoteHouses.isNotEmpty()) {
        // Прошиваем raionId, чтобы дома не "потерялись" при фильтрации в БД
        val housesWithRaion = remoteHouses.map { it.copy(raionId = raionId) }

        database.houseDao().insertHouseList(housesWithRaion)

        // Отдаем актуальные данные из БД (гарантирует порядок сортировки БД)
        val updatedList = database.houseDao().getHousesByRaion(raionId)
        Log.d("YkisLog", "$methodName: [NETWORK_SUCCESS] Список домов обновлен (${updatedList.size})")
        emit(Resource.Success(updatedList))
      } else if (localHouses.isEmpty()) {
        Log.w("YkisLog", "$methodName: [EMPTY_RESULT] Домов в этом районе не найдено")
        emit(Resource.Success(emptyList()))
      }

    } catch (e: java.io.IOException) {
      // КЕЙС: ВАШ СЕРВИС ЛЕЖИТ / НЕТ ИНТЕРНЕТА
      Log.w("YkisLog", "$methodName: [OFFLINE_MODE] Сервис недоступен. Проверка кэша...")

      val cached = database.houseDao().getHousesByRaion(raionId)
      if (cached.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [OFFLINE_RECOVERY] Используем локальный список домов")
        emit(Resource.Success(cached))
        SnackbarManager.showMessage(R.string.error_network)
      } else {
        Log.e("YkisLog", "$methodName: [OFFLINE_FAIL] Кэша нет, сети нет.")
        emit(Resource.Error("Немає зв'язку. Список будинків недоступний."))
        SnackbarManager.showMessage(R.string.error_network)
      }

    } catch (e: Exception) {
      // КРИТИЧЕСКИЕ ОШИБКИ ЛОГИКИ
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      val fallback = database.houseDao().getHousesByRaion(raionId)

      if (fallback.isNotEmpty()) {
        emit(Resource.Success(fallback))
      } else {
        emit(Resource.Error(e.localizedMessage ?: "Помилка завантаження"))
      }
    }
  }.flowOn(Dispatchers.IO)
}









