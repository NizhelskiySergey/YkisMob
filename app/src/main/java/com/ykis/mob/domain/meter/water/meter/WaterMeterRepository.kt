package com.ykis.mob.domain.meter.water.meter


import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.data.remote.water.GetLastWaterReadingResponse
import com.ykis.mob.data.remote.water.GetWaterMeterResponse
import com.ykis.mob.data.remote.water.GetWaterReadingsResponse
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams

interface WaterMeterRepository {
  suspend fun getWaterMeterList( uid: String,addressId: Int,): GetWaterMeterResponse
  suspend fun getWaterReadings( uid: String,vodomerId: Int): GetWaterReadingsResponse
  suspend fun getLastWaterReading(uid: String,vodomerId: Int): GetLastWaterReadingResponse
  suspend fun addWaterReading(params: AddWaterReadingParams): BaseResponse
  suspend fun deleteLastWaterReading(uid: String,readingId: Int ): BaseResponse
}
