package com.ykis.mob.data.remote.water.reading

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.api.ApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams
import retrofit2.await

class
WaterReadingRemoteImpl (
    private val apiService: ApiService
) : WaterReadingRemote {
    override suspend fun getWaterReadings(vodomerId: Int, uid: String):GetWaterReadingsResponse  {
        return apiService.getWaterReadings(
            createGetWaterReadingMap(
                vodomerId, uid
            )
        ).await()
    }

    override suspend fun getLastWaterReading(
        vodomerId: Int,
        uid: String
    ): GetLastWaterReadingResponse {
        return apiService.getLastWaterReading(
            createGetWaterReadingMap(
                vodomerId, uid
            )
        ).await()
    }

    override suspend fun addWaterReading(params: AddWaterReadingParams): GetSimpleResponse {
        return apiService.addWaterReading(
            createAddNewReadingMap(
                vodomerId = params.meterId,
                currentValue = params.currentValue,
                newValue = params.newValue,
                uid = params.uid
            )
        ).await()
    }

    override suspend fun deleteLastReading(readingId: Int , uid:String): BaseResponse {
        return apiService.deleteLastWaterReading(
            createDeleteWaterReadingMap(
                readingId, uid
            )
        ).await()
    }

    private fun createGetWaterReadingMap(
        vodomerId: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.VODOMER_ID] = vodomerId.toString()
        map[ApiService.UID] = uid
        return map
    }

    private fun createAddNewReadingMap(
        vodomerId: Int,
        newValue: Int,
        currentValue: Int,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.VODOMER_ID] = vodomerId.toString()
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
