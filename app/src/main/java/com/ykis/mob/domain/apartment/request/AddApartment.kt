package com.ykis.mob.domain.apartment.request
import android.util.Log
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class AddApartment(
  private val repository: ApartmentRepository,
) {
  operator fun invoke(code: String, uid: String, email: String): Flow<Resource<GetSimpleResponse>> = flow {
    val methodName = "UseCase.AddApartment"
    try {
      Log.d("YkisLog", "$methodName: [START] Code: $code, Email: $email")
      emit(Resource.Loading())

      // ЗАПРОС В СЕТЬ
      val response = repository.addApartmentUser(code, uid, email)

      Log.d("YkisLog", "$methodName: [RESPONSE] Success: ${response.success}, Msg: ${response.message}")

      when {
        response.success == 1 -> {
          Log.d("YkisLog", "$methodName: [SUCCESS] Квартира успешно привязана")
          emit(Resource.Success(response))
        }
        response.message == "FlatAlreadyInDataBase" -> {
          throw ExceptionWithResourceMessage(R.string.error_flat_in_db)
        }
        response.message == "IncorrectCode" -> {
          throw ExceptionWithResourceMessage(R.string.error_incorrect_code)
        }
        else -> {
          Log.e("YkisLog", "$methodName: [SERVER_ERROR] Неизвестный ответ: ${response.message}")
          throw ExceptionWithResourceMessage(R.string.error_add_apartment)
        }
      }

    } catch (e: java.io.IOException) {
      // СЛУЧАЙ: ТВОЙ СЕРВИС ЛЕЖИТ
      Log.e("YkisLog", "$methodName: [NETWORK_FAIL] Сервис недоступен (IOException)")
      emit(Resource.Error(resourceMessage = R.string.error_network, message = "Сервіс тимчасово недоступний"))

    } catch (e: ExceptionWithResourceMessage) {
      Log.w("YkisLog", "$methodName: [LOGIC_ERROR] Ошибка валидации кода")
      emit(Resource.Error(resourceMessage = e.resourceMessage, message = null))

    } catch (ex: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL] Критическая ошибка: ${ex.message}")
      emit(Resource.Error(message = ex.localizedMessage ?: "Непередбачена помилка"))
    }
  }.flowOn(Dispatchers.IO)
}


