package com.ykis.mob.domain.payment.request
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.payment.PaymentRepository
import com.ykis.mob.domain.payment.request.InsertPaymentParams // Проверь импорт
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class InsertPayment (
  private val repository: PaymentRepository,
) {
  // Указываем тип явно в flow<...>, чтобы String? не вызывал Type mismatch
  operator fun invoke(params: InsertPaymentParams): Flow<Resource<String?>> = flow<Resource<String?>> {
    try {
      emit(Resource.Loading())

      val response = repository.insertPayment(params)

      if (response.success == 1) {
        emit(Resource.Success(response.uri))
      } else {
        emit(Resource.Error("Ошибка формирования платежа"))
      }
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error"))
    }
  }.flowOn(Dispatchers.IO) // <--- ПЕРЕНОС В ФОНОВЫЙ ПОТОК
}

