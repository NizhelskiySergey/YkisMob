package com.ykis.mob.data.remote.water.meter

import com.ykis.mob.data.remote.api.ApiService
import retrofit2.await

class WaterMeterRemoteImpl (
    private val apiService: ApiService
) : WaterMeterRemote {

    override suspend fun getWaterMeterList(addressId: Int, uid: String): GetWaterMeterResponse {
        return apiService.getWaterMeterList(
            createGetWaterMeterMap(
                addressId = addressId,
                uid = uid
            )
        ).await()
    }


    private fun createGetWaterMeterMap(
        addressId: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.ADDRESS_ID] = addressId.toString()
        map[ApiService.UID] = uid
        return map
    }


}
