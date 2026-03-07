package com.ykis.mob.data.remote.heat

import com.squareup.moshi.Json
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.meter.HeatMeterEntity
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity

class GetHeatMeterResponse(
    success: Int,
    message: String,
    @Json(name = "heat_meters")
    val heatMeters: List<HeatMeterEntity>
) : BaseResponse(success, message)

class GetHeatReadingResponse(
  success: Int,
  message: String,
  @Json(name = "heat_readings")
  val heatReadings: List<HeatReadingEntity>
) : BaseResponse(success, message)

class GetLastHeatReadingResponse(
  success: Int,
  message: String,
  @Json(name = "heat_reading")
  val heatReading : HeatReadingEntity
) : BaseResponse(success, message)
