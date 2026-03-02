package com.ykis.mob.data

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.appartment.ApartmentRemote
import com.ykis.mob.data.remote.appartment.GetApartmentResponse
import com.ykis.mob.data.remote.appartment.GetApartmentsResponse
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository

class ApartmentRepositoryImpl (
    private val apartmentRemote: ApartmentRemote
) : ApartmentRepository {

    override suspend fun getApartmentList(uid: String): GetApartmentsResponse {
        return apartmentRemote.getApartmentList(uid)
    }

    override suspend fun updateBti(params : ApartmentEntity): BaseResponse {
        return apartmentRemote.updateBti(params)
    }

    override suspend fun getApartment(addressId: Int, uid: String): GetApartmentResponse {
        return apartmentRemote.getApartment(addressId , uid)
    }

    override suspend fun deleteApartment(addressId: Int, uid: String): BaseResponse {
        return apartmentRemote.deleteApartment(addressId , uid)
    }

    override suspend fun addApartmentUser(code: String ,uid :String , email : String): GetSimpleResponse {
        return apartmentRemote.addApartment(code , uid , email)
    }
}
