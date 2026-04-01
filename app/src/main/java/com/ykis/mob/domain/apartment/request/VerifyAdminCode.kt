package com.ykis.mob.domain.apartment.request

import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class VerifyAdminCode(
  private val repository: ApartmentRepository
) {
  operator fun invoke (code : String, uid: String) : Flow<Resource<GetSimpleResponse>> = flow {
    try {
      emit(Resource.Loading())
      // Запрос к вашему API (аналогично addApartmentUser)
      val response = repository.verifyAdminSecretWord(code, uid)

      if (response.success == 1) {
        emit(Resource.Success(response)) // response содержит role и osbbId
      } else {
        throw ExceptionWithResourceMessage(R.string.error_incorrect_admin_code)
      }
    } catch (e: ExceptionWithResourceMessage) {
      emit(Resource.Error(resourceMessage = e.resourceMessage))
    } catch (ex: Exception) {
      emit(Resource.Error(message = ex.message))
    }
  }.flowOn(Dispatchers.IO)
}

