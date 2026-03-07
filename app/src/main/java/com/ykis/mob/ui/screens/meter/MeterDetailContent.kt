package com.ykis.mob.ui.screens.meter

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import com.ykis.mob.core.ext.isTrue
import com.ykis.mob.ui.BaseUIState
import com.ykis.mob.ui.components.EmptyDetail
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.meter.heat.HeatMeterDetail
import com.ykis.mob.ui.screens.meter.heat.HeatMeterState
import com.ykis.mob.ui.screens.meter.heat.reading.HeatReadings
import com.ykis.mob.ui.screens.meter.water.WaterMeterDetail
import com.ykis.mob.ui.screens.meter.water.WaterMeterState
import com.ykis.mob.ui.screens.meter.water.reading.WaterReadings

@Composable
fun MeterDetailContent(
    baseUIState: BaseUIState,
    contentDetail: ContentDetail,
    waterMeterState: WaterMeterState,
    viewModel: MeterViewModel,
    heatMeterState: HeatMeterState
) {
    Crossfade(targetState = contentDetail, label = "") { targetState ->
        when (targetState) {
            ContentDetail.WATER_METER -> {
                WaterMeterDetail(
                    waterMeterEntity = waterMeterState.selectedWaterMeter,
                    baseUIState = baseUIState,
                    getLastReading = {
                        viewModel.getLastWaterReading(
                            vodomerId = waterMeterState.selectedWaterMeter.vodomerId,
                            uid = baseUIState.uid!!
                        )
                    },
                    lastReading = waterMeterState.lastWaterReading,
                    isWorking = !waterMeterState.selectedWaterMeter.spisan.isTrue() && !waterMeterState.selectedWaterMeter.out.isTrue(),
                    isLastReadingLoading = waterMeterState.isLastReadingLoading,
                    newWaterReading = waterMeterState.newWaterReading,
                    onNewReadingChange = { newValue -> viewModel.onNewWaterReadingChange(newValue.filter { it.isDigit() }) },
                    addReading = {
                        viewModel.addWaterReading(
                            uid = baseUIState.uid.toString(),
                            currentValue = waterMeterState.lastWaterReading.current,
                            newValue = waterMeterState.newWaterReading.toInt(),
                            vodomerId = waterMeterState.selectedWaterMeter.vodomerId
                        )
                    },
                    navigateToReadings = {
                        viewModel.setContentDetail(ContentDetail.WATER_READINGS)
                    },
                    deleteReading = {
                        viewModel.deleteLastWaterReading(
                          baseUIState.uid.toString(),
                            waterMeterState.lastWaterReading.vodomerId,
                            waterMeterState.lastWaterReading.pokId

                        )
                    }
                )
            }

            ContentDetail.HEAT_METER -> {
                HeatMeterDetail(
                    heatMeterEntity = heatMeterState.selectedHeatMeter,
                    baseUIState = baseUIState,
                    getLastHeatReading = {
                        viewModel.getLastHeatReading(
                            baseUIState.uid!!,
                            heatMeterState.selectedHeatMeter.teplomerId
                        )
                    },
                    lastHeatReading = heatMeterState.lastHeatReading,
                    isWorking = !heatMeterState.selectedHeatMeter.spisan.isTrue() && !heatMeterState.selectedHeatMeter.out.isTrue(),
                    navigateToReadings = {
                        viewModel.setContentDetail(ContentDetail.HEAT_READINGS)
                    },
                    newHeatReading = heatMeterState.newHeatReading,
                    onNewReadingChange = { newValue ->
                        viewModel.onNewHeatReadingChange(newValue.filter { it.isDefined() })
                    },
                    addReading = {
                        viewModel.addHeatReading(
                            uid = baseUIState.uid.toString(),
                            teplomerId = heatMeterState.selectedHeatMeter.teplomerId,
                            currentValue = heatMeterState.lastHeatReading.current,
                            newValue = heatMeterState.newHeatReading.toDouble()
                        )
                    },
                    deleteReading = {
                        viewModel.deleteLastHeatReading(
                            readingId = heatMeterState.lastHeatReading.pokId,
                            teplomerId = heatMeterState.selectedHeatMeter.teplomerId,
                            uid = baseUIState.uid.toString()
                        )
                    }
                )
            }

            ContentDetail.WATER_READINGS -> {
                WaterReadings(
                    baseUIState = baseUIState,
                    waterMeterState = waterMeterState,
                    getWaterReadings = {
                        viewModel.getWaterReadings(
                            baseUIState.uid!!,
                            waterMeterState.selectedWaterMeter.vodomerId
                        )
                    }
                )
            }

            ContentDetail.HEAT_READINGS -> {
                HeatReadings(
                    baseUIState = baseUIState,
                    heatMeterState = heatMeterState,
                    getHeatReadings = {
                        viewModel.getHeatReadings(
                            baseUIState.uid.toString(),
                            heatMeterState.selectedHeatMeter.teplomerId
                        )
                    }
                )
            }

            else -> EmptyDetail()
        }
    }
}
