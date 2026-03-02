package com.ykis.mob.data.remote.heat.meter

import com.ykis.mob.data.remote.api.ApiService
import retrofit2.await

class HeatMeterRemoteImpl (
    private val apiService: ApiService
) : HeatMeterRemote {

    override suspend fun getHeatMeterList(addressId: Int, uid: String): GetHeatMeterResponse {
        return apiService.getHeatMeterList(
            createGetHeatMeterMap(
                addressId, uid
            )
        ).await()
    }


    private fun createGetHeatMeterMap(
        addressId: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.ADDRESS_ID] = addressId.toString()
        map[ApiService.UID] = uid
        return map
    }
}
