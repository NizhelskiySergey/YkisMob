package com.ykis.mob.ui.screens.meter.water

import com.ykis.mob.domain.meter.water.meter.WaterMeterEntity
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity

data class WaterMeterState(
  val waterMeterList: List<WaterMeterEntity> = emptyList(),
  val waterReadings: List<WaterReadingEntity> = emptyList(),
  val selectedWaterMeter: WaterMeterEntity = WaterMeterEntity(),
  val lastWaterReading: WaterReadingEntity = WaterReadingEntity(),
  val newWaterReading: String = "",
  val isReadingError: Boolean = false,
  val isMetersLoading: Boolean = false,
  val isReadingsLoading: Boolean = false,
  val isLastReadingLoading: Boolean = false,
  val error: String? = null
)
