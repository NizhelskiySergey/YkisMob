
package com.ykis.mob.data.remote.appartment

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.api.ApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity
import retrofit2.await

class ApartmentRemoteImpl (
    private val apiService: ApiService
) : ApartmentRemote {

    override suspend fun getApartmentList(uid: String): GetApartmentsResponse {
        return apiService.getApartmentList(
            createGetApartmentListMap(
                uid
            )
            // TODO: read about Call class
        ).await()
    }

    override suspend fun updateBti(params: ApartmentEntity): BaseResponse {
        return apiService.updateBti(
            createUpdateBti(
                addressId = params.addressId,
                phone = params.phone,
                email = params.email,
                uid = params.uid ?: ""
            )
        ).await()
    }

    override suspend fun getApartment(addressId: Int, uid: String): GetApartmentResponse {
        return apiService.getApartment(
            createRequestByAddressId(
                addressId = addressId,
                uid = uid
            )
        ).await()
    }

    override suspend fun deleteApartment(addressId: Int, uid: String): BaseResponse {
        return apiService.deleteApartment(
            createRequestByAddressId(
                addressId,uid
            )
        ).await()
    }

    override suspend fun addApartment(code: String , uid:String , email: String): GetSimpleResponse {
        return apiService.addApartment(
            createAddApartmentMap(
                code = code,
                uid = uid,
                email = email
            )
        ).await()
    }

    private fun createGetApartmentListMap(uid: String): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.UID] = uid
        return map
    }

    private fun createRequestByAddressId(
        addressId: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.PARAM_ADDRESS_ID] = addressId.toString()
        map[ApiService.UID] = uid
        return map
    }

    private fun createUpdateBti(
        addressId: Int,
        phone: String,
        email: String,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.ADDRESS_ID] = addressId.toString()
        map[ApiService.PHONE] = phone
        map[ApiService.EMAIL] = email
        map[ApiService.UID] = uid
        return map
    }
    private fun createAddApartmentMap(code: String, uid: String ,email: String): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.CODE] = code
        map[ApiService.UID] = uid
        map[ApiService.EMAIL] = email
        return map
    }
}
