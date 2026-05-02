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

      // 1. ПРОВЕРКА ЛОКАЛЬНОЙ БАЗЫ (Мгновенный результат)
      val localApartment = database.apartmentDao().getFlatById(addressId = addressId)
      if (localApartment != null) {
        Log.d("YkisLog", "$methodName: [LOCAL_HIT] Найдена в Room. Отдаем для быстрой отрисовки.")
        emit(Resource.Success(localApartment))
      }

      // 2. ЗАПРОС В СЕТЬ (Попытка обновления)
      Log.d("YkisLog", "$methodName: [NETWORK_START] Запрос ID: $addressId")
      val response = repository.getApartment(addressId, uid)

      if (response.success == 1) {
        response.apartment?.let { remoteApartment ->
          // Прошиваем актуальный UID
          val apartmentWithUid = remoteApartment.copy(uid = uid)

          // Обновляем кэш в базе
          database.apartmentDao().insertApartmentList(listOf(apartmentWithUid))
          Log.d("YkisLog", "$methodName: [NETWORK_SUCCESS] Данные обновлены в БД")

          // Отдаем свежие данные
          emit(Resource.Success(apartmentWithUid))
        } ?: run {
          Log.e("YkisLog", "$methodName: [NETWORK_EMPTY] Сервер вернул успех 1, но объект null")
          emit(Resource.Error("Дані квартири порожні"))
        }
      } else {
        Log.e("YkisLog", "$methodName: [SERVER_REJECT] Сервер вернул ошибку: ${response.success}")
        emit(Resource.Error("Помилка сервера"))
      }

    } catch (e: IOException) {
      // ЭТОТ БЛОК СРАБОТАЕТ, ЕСЛИ СЕРВИС ЛЕЖИТ ИЛИ НЕТ ИНТЕРНЕТА
      Log.w("YkisLog", "$methodName: [IO_EXCEPTION] Сервис недоступен. Проверка резервного кэша...")

      val cached = database.apartmentDao().getFlatById(addressId = addressId)

      if (cached != null) {
        // ВАЖНО: Если данные в кэше есть — мы НЕ ШЛЕМ Resource.Error!
        // Мы подтверждаем успех, чтобы UI продолжал работать на старых данных.
        Log.d("YkisLog", "$methodName: [OFFLINE_RECOVERY] Работаем на кэше. UI не падает.")
        emit(Resource.Success(cached))
        SnackbarManager.showMessage(R.string.error_network) // Просто информируем "тихо"
      } else {
        // Только если и в базе пусто (первый вход без сети) — шлем реальную ошибку
        Log.e("YkisLog", "$methodName: [OFFLINE_FAIL] Кэша нет, сети нет.")
        emit(Resource.Error("Немає зв'язку. Будь ласка, підключіться до мережі."))
        SnackbarManager.showMessage(R.string.error_network)
      }

    } catch (e: ExceptionWithResourceMessage) {
      Log.e("YkisLog", "$methodName: [LOGIC_ERROR] ${e.localizedMessage}")
      SnackbarManager.showMessage(e.resourceMessage)
      emit(Resource.Error(e.localizedMessage ?: "Error"))

    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL] Непредвиденная ошибка: ${e.message}")
      emit(Resource.Error("Непередбачена помилка"))
    }
  }.flowOn(Dispatchers.IO)
}


