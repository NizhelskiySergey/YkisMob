package com.ykis.mob.data

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.data.remote.heat.reading.GetHeatReadingResponse
import com.ykis.mob.data.remote.heat.reading.GetLastHeatReadingResponse
import com.ykis.mob.data.remote.heat.reading.HeatReadingRemote
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams
import com.ykis.mob.domain.meter.heat.reading.HeatReadingRepository
class HeatReadingRepositoryImpl (
    private val heatReadingRemote: HeatReadingRemote,
) : HeatReadingRepository {

    override suspend fun getHeatReadings(teplomerId: Int, uid: String): GetHeatReadingResponse {
        return heatReadingRemote.getHeatReadings(teplomerId, uid)
    }

    override suspend fun getLastHeatReading(
        teplomerId: Int,
        uid: String
    ): GetLastHeatReadingResponse {
        return heatReadingRemote.getLastHeatReading(teplomerId, uid)
    }

    override suspend fun addHeatReading(params: AddHeatReadingParams): BaseResponse {
        return heatReadingRemote.addHeatReading(params)
    }

    override suspend fun deleteLastHeatReading(readingId: Int, uid: String): BaseResponse {
        return heatReadingRemote.deleteLastHeatReading(readingId, uid)
    }

}
