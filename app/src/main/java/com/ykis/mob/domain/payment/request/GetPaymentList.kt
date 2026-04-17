package com.ykis.mob.domain.payment.request
import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.payment.PaymentEntity
import com.ykis.mob.domain.payment.PaymentRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.Dispatchers // ДОБАВЛЕНО
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВЛЕНО
import kotlinx.coroutines.withContext
import java.io.IOException

class GetPaymentList(
  private val repository: PaymentRepository,
  private val database: AppDatabase
) {
  operator fun invoke(addressId: Int, year: String, uid: String): Flow<Resource<List<PaymentEntity>>> = flow {
    val methodName = "GetPaymentList"
    try {
      Log.d("YkisLog", "$methodName: [START] AddrID: $addressId, Year: $year")
      emit(Resource.Loading())

      // 1. ПРОВЕРКА ЛОКАЛЬНОЙ БАЗЫ
      val localPayments = database.paymentDao().getPaymentFromFlat(addressId)
      if (localPayments.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [DB_HIT] Найдено в базе: ${localPayments.size}")
        emit(Resource.Success(localPayments))
      } else {
        Log.d("YkisLog", "$methodName: [DB_MISS] В базе пусто")
      }

      // 2. ЗАПРОС В СЕТЬ
      Log.d("YkisLog", "$methodName: [NETWORK_REQ] Отправка запроса...")
      val response = repository.getPaymentList(addressId, year, uid)

      if (response.success == 1) {
        val remotePayments = response.payments ?: emptyList()
        Log.d("YkisLog", "$methodName: [NETWORK_SUCCESS] Получено: ${remotePayments.size}")

        emit(Resource.Success(remotePayments))

        // 3. СОХРАНЕНИЕ В БАЗУ
        withContext(Dispatchers.IO) {
          database.paymentDao().insertPayment(remotePayments)
          Log.d("YkisLog", "$methodName: [DB_SAVE] Данные обновлены в Room")
        }
      } else {
        Log.w("YkisLog", "$methodName: [SERVER_REJECT] Success=0, Message: ${response.message}")
        emit(Resource.Error(message = response.message))
      }

    } catch (e: ResponseException) {
      Log.e("YkisLog", "$methodName: [HTTP_ERROR] ${e.response.status}")
      SnackbarManager.showMessage(e.response.status.description)
      emit(Resource.Error())
    } catch (e: IOException) {
      Log.e("YkisLog", "$methodName: [IO_ERROR] Проблема с сетью")
      val paymentList = database.paymentDao().getPaymentFromFlat(addressId)
      if (paymentList.isNotEmpty()) {
        Log.d("YkisLog", "$methodName: [OFFLINE_MODE] Показ данных из кэша")
        emit(Resource.Success(paymentList))
      } else {
        SnackbarManager.showMessage(R.string.error_network)
        emit(Resource.Error())
      }
    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [CRITICAL_ERROR] ${e.message}")
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // Гарантируем работу в фоновом потоке
}


