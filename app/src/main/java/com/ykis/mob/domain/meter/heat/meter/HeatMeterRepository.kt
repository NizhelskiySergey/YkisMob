package com.ykis.mob.domain.meter.heat.meter

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.data.remote.heat.GetHeatMeterResponse
import com.ykis.mob.data.remote.heat.GetHeatReadingResponse
import com.ykis.mob.data.remote.heat.GetLastHeatReadingResponse
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams

interface HeatMeterRepository {
  suspend fun getHeatMeterList(uid: String, addressId: Int): GetHeatMeterResponse
  suspend fun getHeatReadings(uid: String, teplomerId: Int): GetHeatReadingResponse
  suspend fun getLastHeatReading(uid: String, teplomerId: Int): GetLastHeatReadingResponse
  suspend fun addHeatReading(params: AddHeatReadingParams): BaseResponse
  suspend fun deleteLastHeatReading(uid: String, readingId: Int): BaseResponse
}
