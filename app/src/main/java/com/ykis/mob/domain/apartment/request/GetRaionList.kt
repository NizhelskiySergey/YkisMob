package com.ykis.mob.domain.apartment.request
import android.util.Log
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import com.ykis.mob.domain.apartment.RaionEntity
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class GetRaionList(
  private val repository: ApartmentRepository,
  private val database: AppDatabase
) {
  operator fun invoke(uid: String): Flow<Resource<List<RaionEntity>>> = flow {
    val methodName = "UseCase.GetRaionList"
    try {
      emit(Resource.Loading())

      // 1. ПРОВЕРКА КЭША
      val localRaions = database.raionDao().getRaionList()
      Log.d("YkisLog", "$methodName: [DB_READ] Найдено в базе: ${localRaions.size} шт.")

      if (localRaions.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [LOCAL_EMIT] Отправляем кэш в UI")
        emit(Resource.Success(localRaions))
      }

      // 2. СЕТЕВОЙ ЗАПРОС
      Log.d("YkisLog", "$methodName: [NETWORK_START] Запрос для UID: $uid")
      val response = repository.getRaionList(uid)

      // Логируем сырой ответ (если возможно) или количество распарсенных объектов
      val remoteRaions = response.raions ?: emptyList()
      Log.d("YkisLog", "$methodName: [NETWORK_RESULT] Успех: ${response.success}, Сообщение: ${response.message}, Объектов распарсено: ${remoteRaions.size}")

      if (remoteRaions.isNotEmpty()) {
        // 3. ОБНОВЛЕНИЕ БАЗЫ
        Log.d("YkisLog", "$methodName: [DB_WRITE] Попытка вставки ${remoteRaions.size} записей в Room...")
        database.raionDao().insertRaionList(remoteRaions)

        // СРАЗУ ПРОВЕРЯЕМ ЗАПИСЬ
        val verifyDb = database.raionDao().getRaionList()
        Log.d("YkisLog", "$methodName: [DB_VERIFY] После записи в Room стало: ${verifyDb.size} строк")

        if (verifyDb.isEmpty()) {
          Log.e("YkisLog", "$methodName: [CRITICAL] Вставка была вызвана, но база пуста! Проверьте типы данных в RaionEntity.")
        }

        emit(Resource.Success(verifyDb))
      } else {
        Log.w("YkisLog", "$methodName: [EMPTY_RESULT] Сеть вернула 0 районов. Success в UI.")
        if (localRaions.isEmpty()) emit(Resource.Success(emptyList()))
      }

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] Тип: ${e.javaClass.simpleName}, Сообщение: ${e.message}")

      val fallback = database.raionDao().getRaionList()
      if (fallback.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [ERROR_FALLBACK] Выдаем старые данные из кэша")
        emit(Resource.Success(fallback))
      } else {
        emit(Resource.Error(e.localizedMessage ?: "Помилка завантаження районів"))
      }
    }
  }.flowOn(Dispatchers.IO)
}







