package com.ykis.mob.domain.apartment.request
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

class UpdateBti (
  private val repository: ApartmentRepository,
){
  operator fun invoke (params: ApartmentEntity) : Flow<Resource<BaseResponse>> = flow {
    try {
      emit(Resource.Loading())
      val response = repository.updateBti(params)

      if (response.success == 1) {
        emit(Resource.Success(response))
      } else {
        throw ExceptionWithResourceMessage(R.string.error_update)
      }
    } catch (e: ExceptionWithResourceMessage) {
      emit(Resource.Error(resourceMessage = e.resourceMessage, message = null))
    } catch (ex: Exception) {
      emit(Resource.Error(message = ex.message))
    }
  }.flowOn(Dispatchers.IO) // <--- ГАРАНТИРУЕТ ПЛАВНУЮ РАБОТУ UI
}

