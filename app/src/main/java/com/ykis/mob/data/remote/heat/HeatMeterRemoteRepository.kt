package com.ykis.mob.data.remote.heat

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams

interface HeatMeterRemoteRepository {
    suspend fun getHeatMeterList(uid: String,addressId: Int) : GetHeatMeterResponse
  suspend fun getHeatReadings(uid: String,teplomerId: Int):GetHeatReadingResponse
  suspend fun getLastHeatReading(uid: String,teplomerId: Int):GetLastHeatReadingResponse
  suspend fun addHeatReading(params : AddHeatReadingParams):BaseResponse
  suspend fun deleteLastHeatReading(uid: String,readingId:Int):BaseResponse
}
