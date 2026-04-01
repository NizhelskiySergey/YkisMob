package com.ykis.mob.domain.apartment

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.appartment.GetApartmentResponse
import com.ykis.mob.data.remote.appartment.GetApartmentsResponse
import com.ykis.mob.data.remote.core.BaseResponse

interface ApartmentRepository {
  suspend fun getApartmentList(uid: String): GetApartmentsResponse
  suspend fun updateBti(params: ApartmentEntity): BaseResponse
  suspend fun getApartment(addressId: Int, uid: String): GetApartmentResponse
  suspend fun deleteApartment(addressId: Int, uid: String): BaseResponse
  suspend fun addApartmentUser(code: String, uid: String, email: String): GetSimpleResponse
  suspend fun verifyAdminSecretWord(code: String, uid: String): GetSimpleResponse
  suspend fun saveUserUid(uid: String,email: String): GetSimpleResponse
  suspend fun deleteUserAccount(uid: String,email: String): GetSimpleResponse

}
