package com.ykis.mob.data.remote.heat

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.meter.HeatMeterEntity
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GetHeatMeterResponse(
  @SerialName("success") override val success: Int,
  @SerialName("message") override val message: String,

  @SerialName("heat_meters")
  val heatMeters: List<HeatMeterEntity> = emptyList()
) : BaseResponse

@Serializable
class GetHeatReadingResponse(
  @SerialName("success") override val success: Int,
  @SerialName("message") override val message: String,

  @SerialName("heat_readings")
  val heatReadings: List<HeatReadingEntity> = emptyList()
) : BaseResponse

@Serializable
class GetLastHeatReadingResponse(
  @SerialName("success") override val success: Int,
  @SerialName("message") override val message: String,

  @SerialName("heat_reading")
  val heatReading: HeatReadingEntity? = null
) : BaseResponse
