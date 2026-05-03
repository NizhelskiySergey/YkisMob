package com.ykis.mob.data.remote.appartment

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.ui.screens.chat.SendNotificationArguments

interface ApartmentRemote {
  suspend fun getApartmentList(uid: String): GetApartmentsResponse
  suspend fun getOsbbApartmentsList(targetId: Int,isHouse: Boolean): GetApartmentsResponse
  suspend fun getRaionList(uid: String): GetRaionsResponse
  suspend fun getHouseByRaionList(raionId: Int): GetHousesResponse
  suspend fun updateBti(params: ApartmentEntity): BaseResponse
  suspend fun getApartment(addressId: Int, uid: String): GetApartmentResponse
  suspend fun deleteApartment(addressId: Int, uid: String): BaseResponse
  suspend fun addApartment(code: String, uid: String, email: String): GetSimpleResponse
  suspend fun verifyAdminSecretWord(code: String, uid: String): GetSimpleResponse
  suspend fun saveUserUid(uid: String, email: String): GetSimpleResponse
  suspend fun deleteUserAccount(uid: String, email: String): GetSimpleResponse
}
