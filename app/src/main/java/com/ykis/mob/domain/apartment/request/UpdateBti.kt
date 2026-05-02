package com.ykis.mob.domain.apartment.request
import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class UpdateBti(
  private val repository: ApartmentRepository,
) {
  operator fun invoke(params: ApartmentEntity): Flow<Resource<BaseResponse>> = flow {
    val methodName = "UseCase.UpdateBti"
    try {
      Log.d("YkisLog", "$methodName: [START] Обновление данных для о/р: ${params.addressId}")
      emit(Resource.Loading())

      // 1. ЗАПРОС В СЕТЬ (Обновление характеристик БТИ)
      val response = repository.updateBti(params)

      Log.d("YkisLog", "$methodName: [RESPONSE] Success: ${response.success}, Message: ${response.message}")

      if (response.success == 1) {
        Log.d("YkisLog", "$methodName: [SUCCESS] Характеристики обновлены в MySQL")
        emit(Resource.Success(response))
      } else {
        Log.e("YkisLog", "$methodName: [SERVER_REJECT] Ошибка логики: ${response.message}")
        throw ExceptionWithResourceMessage(R.string.error_update)
      }

    } catch (e: java.io.IOException) {
      // СЛУЧАЙ: ВАШ СЕРВИС ЛЕЖИТ / НЕТ СЕТИ
      Log.e("YkisLog", "$methodName: [NETWORK_FAIL] Сервис недоступен (IOException)")
      emit(Resource.Error(
        resourceMessage = R.string.error_network,
        message = "Неможливо оновити дані БТІ без з'єднання з сервером"
      ))

    } catch (e: ExceptionWithResourceMessage) {
      // Ошибка, которую мы выбросили сами (success != 1)
      emit(Resource.Error(resourceMessage = e.resourceMessage))

    } catch (ex: Exception) {
      // Критическая ошибка (сбой парсинга, timeout и т.д.)
      Log.e("YkisLog", "$methodName: [FATAL] Непередбачена помилка: ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage ?: "Помилка оновлення бази"))
    }
  }.flowOn(Dispatchers.IO)
}


