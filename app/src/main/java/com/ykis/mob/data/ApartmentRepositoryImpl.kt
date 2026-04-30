package com.ykis.mob.data

import android.R.attr.targetId
import com.google.android.play.integrity.internal.u
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.appartment.ApartmentRemote
import com.ykis.mob.data.remote.appartment.GetApartmentResponse
import com.ykis.mob.data.remote.appartment.GetApartmentsResponse
import com.ykis.mob.data.remote.appartment.GetHousesResponse
import com.ykis.mob.data.remote.appartment.GetRaionsResponse
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import com.ykis.mob.domain.apartment.request.GetHouseList

class ApartmentRepositoryImpl(
  private val apartmentRemote: ApartmentRemote
) : ApartmentRepository {

  override suspend fun getApartmentList(uid: String): GetApartmentsResponse {
    return apartmentRemote.getApartmentList(uid)
  }
  override suspend fun getOsbbApartmentsList(targetId:Int,isHouse: Boolean): GetApartmentsResponse{
    return apartmentRemote.getOsbbApartmentsList(targetId,isHouse)
  }
  override suspend fun getHouseByRaionList(raionId:Int): GetHousesResponse {
    return apartmentRemote.getHouseByRaionList(raionId)
  }
  override suspend fun getRaionList(uid: String): GetRaionsResponse{
    return apartmentRemote.getRaionList(uid)
  }

  override suspend fun updateBti(params: ApartmentEntity): BaseResponse {
    return apartmentRemote.updateBti(params)
  }

  override suspend fun getApartment(addressId: Int, uid: String): GetApartmentResponse {
    return apartmentRemote.getApartment(addressId, uid)
  }

  override suspend fun deleteApartment(addressId: Int, uid: String): BaseResponse {
    return apartmentRemote.deleteApartment(addressId, uid)
  }

  override suspend fun addApartmentUser(
    code: String,
    uid: String,
    email: String
  ): GetSimpleResponse {
    return apartmentRemote.addApartment(code, uid, email)
  }

  override suspend fun verifyAdminSecretWord(
    code: String,
    uid: String
  ): GetSimpleResponse {
    return apartmentRemote.verifyAdminSecretWord(code, uid)
  }

  override suspend fun saveUserUid(
    uid: String,
    email: String
  ): GetSimpleResponse {
    return apartmentRemote.saveUserUid(uid,email)
  }

  override suspend fun deleteUserAccount(
    uid: String,
    email: String
  ): GetSimpleResponse {
    return apartmentRemote.deleteUserAccount(uid,email)
  }


}
