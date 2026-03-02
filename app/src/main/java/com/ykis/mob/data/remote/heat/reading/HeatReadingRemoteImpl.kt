package com.ykis.mob.data.remote.heat.reading

import com.ykis.mob.data.remote.api.ApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams
import retrofit2.await

class HeatReadingRemoteImpl (
    private val apiService: ApiService
) : HeatReadingRemote {
    override suspend fun getHeatReadings(teplomerId: Int, uid: String): GetHeatReadingResponse {
        return apiService.getHeatReadings(
            createGetHeatReadingMap(
                teplomerId,uid
            )
        ).await()
    }

    override suspend fun getLastHeatReading(teplomerId: Int , uid:String): GetLastHeatReadingResponse {
       return apiService.getLastHeatReading(
           createGetHeatReadingMap(
               teplomerId, uid
           )
       ).await()
    }

    override suspend fun addHeatReading(params: AddHeatReadingParams): BaseResponse {
        return apiService.addHeatReading(
            createAddReadingMap(
                teplomerId = params.meterId,
                currentValue = params.currentValue,
                newValue = params.newValue,
                uid = params.uid
            )
        ).await()
    }

    override suspend fun deleteLastHeatReading(readingId: Int, uid: String): BaseResponse {
        return apiService.deleteLastHeatReading(
            createDeleteWaterReadingMap(
                readingId , uid
            )
        ).await()
    }

    private fun createGetHeatReadingMap(
        teplomerId: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.TEPLOMER_ID] = teplomerId.toString()
        map[ApiService.UID] = uid
        return map
    }

    private fun createAddReadingMap(
        teplomerId: Int,
        newValue: Double,
        currentValue: Double,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.TEPLOMER_ID] = teplomerId.toString()
        map[ApiService.NEW_VALUE] = newValue.toString()
        map[ApiService.CURRENT_VALUE] = currentValue.toString()
        map[ApiService.UID] = uid
        return map
    }

    private fun createDeleteWaterReadingMap(
        pokId: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.POK_ID] = pokId.toString()
        map[ApiService.UID] = uid
        return map
    }
}
