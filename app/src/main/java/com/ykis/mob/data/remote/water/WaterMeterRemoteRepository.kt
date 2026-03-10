package com.ykis.mob.data.remote.water

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams

interface WaterMeterRemoteRepository {
  suspend fun getWaterMeterList(uid: String, addressId: Int): GetWaterMeterResponse
  suspend fun getWaterReadings(uid: String, vodomerId: Int): GetWaterReadingsResponse
  suspend fun getLastWaterReading(uid: String, vodomerId: Int): GetLastWaterReadingResponse
  suspend fun addWaterReading(params: AddWaterReadingParams): GetSimpleResponse
  suspend fun deleteLastReading(uid: String, readingId: Int): GetSimpleResponse
}
