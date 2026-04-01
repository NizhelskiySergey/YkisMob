
package com.ykis.mob.data.remote.appartment

import com.ykis.mob.core.Constants.ADDRESS_ID
import com.ykis.mob.core.Constants.CODE
import com.ykis.mob.core.Constants.EMAIL
import com.ykis.mob.core.Constants.PARAM_ADDRESS_ID
import com.ykis.mob.core.Constants.PHONE
import com.ykis.mob.core.Constants.UID
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.api.KtorApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity

class ApartmentRemoteImpl (
    private val ktorApiService: KtorApiService
) : ApartmentRemote {



  override suspend fun getApartmentList(uid: String): GetApartmentsResponse {
    // Убрали .await(), Ktor сразу возвращает GetApartmentsResponse
    return ktorApiService.getApartmentList(createGetApartmentListMap(uid))
  }

  override suspend fun updateBti(params: ApartmentEntity): BaseResponse {
    return ktorApiService.updateBti(
      createUpdateBti(
        addressId = params.addressId,
        phone = params.phone,
        email = params.email,
        uid = params.uid ?: ""
      )
    )
  }

  override suspend fun getApartment(addressId: Int, uid: String): GetApartmentResponse {
    return ktorApiService.getApartment(
      createRequestByAddressId(
        addressId = addressId,
        uid = uid
      )
    )
  }

  override suspend fun deleteApartment(addressId: Int, uid: String): BaseResponse {
    return ktorApiService.deleteApartment(
      createRequestByAddressId(addressId, uid)
    )
  }

  override suspend fun addApartment(code: String, uid: String, email: String): GetSimpleResponse {
    return ktorApiService.addApartment(
      createAddApartmentMap(
        code = code,
        uid = uid,
        email = email
      )
    )
  }

  override suspend fun verifyAdminSecretWord(
    code: String,
    uid: String
  ): GetSimpleResponse {
    return ktorApiService.verifyAdminSecretWord(
      createVerifyAdminSecretWord(
        code = code,
        uid = uid

      )
    )
  }

  override suspend fun saveUserUid(
    uid: String,
    email: String
  ): GetSimpleResponse {
    return ktorApiService.saveUserUid(
      createSaveUserUid(
        uid=uid,
        email=email

      )
    )
  }

  override suspend fun deleteUserAccount(
    uid: String,
    email: String
  ): GetSimpleResponse {
    return ktorApiService.deleteUserAccount(
      createDeleteUserAccount(
        uid=uid,
        email=email

      )
    )
  }

  private fun createGetApartmentListMap(uid: String): Map<String, String> {
        val map = HashMap<String, String>()
        map[UID] = uid
        return map
    }

    private fun createRequestByAddressId(
        addressId: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[PARAM_ADDRESS_ID] = addressId.toString()
        map[UID] = uid
        return map
    }

    private fun createUpdateBti(
        addressId: Int,
        phone: String,
        email: String,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ADDRESS_ID] = addressId.toString()
        map[PHONE] = phone
        map[EMAIL] = email
        map[UID] = uid
        return map
    }
    private fun createAddApartmentMap(code: String, uid: String ,email: String): Map<String, String> {
        val map = HashMap<String, String>()
        map[CODE] = code
        map[UID] = uid
        map[EMAIL] = email
        return map
    }
  private fun createVerifyAdminSecretWord(code: String, uid: String ): Map<String, String> {
        val map = HashMap<String, String>()
        map[CODE] = code
        map[UID] = uid
        return map
    }
  private fun createSaveUserUid(uid: String,email: String ): Map<String, String> {
    val map = HashMap<String, String>()
    map[UID] = uid
    map[EMAIL] = email
    return map
  }
  private fun createDeleteUserAccount(uid: String,email: String ): Map<String, String> {
    val map = HashMap<String, String>()
    map[UID] = uid
    map[EMAIL] = email
    return map
  }
}
