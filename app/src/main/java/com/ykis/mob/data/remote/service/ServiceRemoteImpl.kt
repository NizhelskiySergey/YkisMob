package com.ykis.mob.data.remote.service

import com.ykis.mob.data.remote.api.ApiService
import com.ykis.mob.domain.service.request.ServiceParams
import retrofit2.await

class ServiceRemoteImpl(
    private val apiService: ApiService
) : ServiceRemote {
    override suspend fun getFlatDetailServices(params: ServiceParams): GetServiceResponse {
        return apiService.getFlatService(
                createGetFlatServiceMap(
                    params.uid,
                    params.addressId,
                    params.houseId,
                    params.year,
                    params.service,
                    params.total
                )
            ).await()
    }

    override suspend fun getTotalDebtService(params: ServiceParams): GetServiceResponse {
        return apiService.getFlatService(
            createGetFlatServiceMap(
                params.uid,
                params.addressId,
                params.houseId,
                params.year,
                params.service,
                params.total
            )
        ).await()
    }

    private fun createGetFlatServiceMap(
        uid: String,
        addressId: Int,
        houseId: Int,
        year: String,
        service: Byte,
        total: Byte,
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.UID] = uid
        map[ApiService.ADDRESS_ID] = addressId.toString()
        map[ApiService.HOUSE_ID] = houseId.toString()
        map[ApiService.YEAR] = year
        map[ApiService.SERVICE] = service.toString()
        map[ApiService.TOTAL] = total.toString()
        return map
    }

}
