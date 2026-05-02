package com.ykis.mob.domain.apartment.request
import android.util.Log
import androidx.room.withTransaction
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
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

      // 1. ПРОВЕРКА КЭША (Мгновенное отображение при старте)
      val localRaions = database.raionDao().getRaionList()
      if (localRaions.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [LOCAL_HIT] Найдено ${localRaions.size} районов в Room")
        emit(Resource.Success(localRaions))
      }

      // 2. СЕТЕВОЙ ЗАПРОС (Попытка обновления)
      Log.d("YkisLog", "$methodName: [NETWORK_START] Запрос для UID: $uid")
      val response = repository.getRaionList(uid)
      val remoteRaions = response.raions ?: emptyList()

      // 3. ОБНОВЛЕНИЕ БАЗЫ
      if (remoteRaions.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [DB_WRITE] Синхронизация ${remoteRaions.size} записей")

        database.withTransaction {
          // Очищаем старое только если пришло новое, чтобы избежать "мерцания" пустотой
          database.raionDao().insertRaionList(remoteRaions)
        }

        val updatedList = database.raionDao().getRaionList()
        emit(Resource.Success(updatedList))
      } else if (localRaions.isEmpty()) {
        Log.w("YkisLog", "$methodName: [EMPTY] Районов не найдено ни в сети, ни в БД")
        emit(Resource.Success(emptyList()))
      }

    } catch (e: java.io.IOException) {
      // КЕЙС: ВАШ СЕРВИС ЛЕЖИТ / НЕТ ИНТЕРНЕТА
      Log.w("YkisLog", "$methodName: [OFFLINE_MODE] Сервис недоступен. Проверка кэша...")

      val cached = database.raionDao().getRaionList()
      if (cached.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [OFFLINE_RECOVERY] Работаем на локальном списке")
        emit(Resource.Success(cached))
        // Уведомляем пользователя, что данные могут быть не самыми свежими
        SnackbarManager.showMessage(R.string.error_network)
      } else {
        Log.e("YkisLog", "$methodName: [OFFLINE_FAIL] Сети нет и база пуста")
        emit(Resource.Error("Немає зв'язку. Список районів недоступний."))
        SnackbarManager.showMessage(R.string.error_network)
      }

    } catch (e: Exception) {
      // КРИТИЧЕСКИЕ ОШИБКИ (например, сбой парсинга JSON)
      Log.e("YkisLog", "$methodName: [FATAL_ERROR] ${e.message}")
      val fallback = database.raionDao().getRaionList()

      if (fallback.isNotEmpty()) {
        emit(Resource.Success(fallback))
      } else {
        emit(Resource.Error(e.localizedMessage ?: "Помилка завантаження списку"))
      }
    }
  }.flowOn(Dispatchers.IO)
}








