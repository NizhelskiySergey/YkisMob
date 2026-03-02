package com.ykis.mob.domain.apartment.request
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ

class AddApartment (
  private val repository: ApartmentRepository,
){
  operator fun invoke (code : String, uid: String, email: String) : Flow<Resource<GetSimpleResponse>> = flow {
    try {
      emit(Resource.Loading())
      val response = repository.addApartmentUser(code, uid, email)

      when {
        response.success == 1 -> emit(Resource.Success(response))
        response.message == "FlatAlreadyInDataBase" -> throw ExceptionWithResourceMessage(R.string.error_flat_in_db)
        response.message == "IncorrectCode" -> throw ExceptionWithResourceMessage(R.string.error_incorrect_code)
        else -> throw ExceptionWithResourceMessage(R.string.error_add_apartment)
      }
    }
    catch (e: ExceptionWithResourceMessage) {
      emit(Resource.Error(resourceMessage = e.resourceMessage, message = null))
    } catch (ex: Exception) {
      emit(Resource.Error(message = ex.message))
    }
  }.flowOn(Dispatchers.IO) // <--- ГАРАНТИРУЕТ ПЛАВНОСТЬ UI ПРИ СЕТЕВОМ ЗАПРОСЕ
}

