package com.ykis.mob.domain.payment.request
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
import java.io.IOException

class GetPaymentList (
  private val repository: PaymentRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<PaymentEntity>, чтобы типы внутри flow совпадали
  operator fun invoke(addressId: Int, year: String, uid: String): Flow<Resource<List<PaymentEntity>>> = flow {
    try {
      emit(Resource.Loading())

      // Сначала проверим локальные данные
      val localPayments = database.paymentDao().getPaymentFromFlat(addressId)
      if (localPayments.isNotEmpty()) {
        emit(Resource.Success(localPayments))
      }

      // Запрос в сеть
      val response = repository.getPaymentList(addressId, year, uid)

      if (response.success == 1) {
        val remotePayments = response.payments ?: emptyList()
        emit(Resource.Success(remotePayments))

        // Сохранение в базу теперь безопасно на IO потоке
        database.paymentDao().insertPayment(remotePayments)
      }

    } catch (e: ResponseException) {
      // Ktor выбрасывает это при ошибках 4xx, 5xx и т.д.
      SnackbarManager.showMessage(e.response.status.description)
      emit(Resource.Error())
    } catch (e: IOException) {
      val paymentList = database.paymentDao().getPaymentFromFlat(addressId)
      if (paymentList.isNotEmpty()) {
        emit(Resource.Success(paymentList))
        return@flow
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // <--- ФИНАЛЬНЫЙ ФИКС КРЭША
}

