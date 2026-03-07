package com.ykis.mob.data.remote.water

import com.squareup.moshi.Json
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.meter.WaterMeterEntity
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity

class GetWaterMeterResponse(
    success: Int,
    message: String,
    @Json(name = "water_meters")
    val waterMeters: List<WaterMeterEntity>
) : BaseResponse(success, message)

class GetLastWaterReadingResponse(
  success: Int,
  message: String,
  @Json(name = "water_reading")
  val waterReading: WaterReadingEntity
) : BaseResponse(success, message)

class GetWaterReadingsResponse(
  success: Int,
  message: String,
  @Json(name = "water_readings")
  val waterReadings: List<WaterReadingEntity>
) : BaseResponse(success, message)
