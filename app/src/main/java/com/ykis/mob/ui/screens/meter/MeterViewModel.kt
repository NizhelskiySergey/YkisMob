package com.ykis.mob.ui.screens.meter

import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.domain.meter.heat.meter.HeatMeterEntity
import com.ykis.mob.domain.meter.heat.meter.request.GetHeatMeterList
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity
import com.ykis.mob.domain.meter.heat.reading.request.AddHeatReading
import com.ykis.mob.domain.meter.heat.reading.request.DeleteLastHeatReading
import com.ykis.mob.domain.meter.heat.reading.request.GetHeatReadings
import com.ykis.mob.domain.meter.heat.reading.request.GetLastHeatReading
import com.ykis.mob.domain.meter.water.meter.WaterMeterEntity
import com.ykis.mob.domain.meter.water.meter.request.GetWaterMeterList
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity
import com.ykis.mob.domain.meter.water.reading.request.AddWaterReading
import com.ykis.mob.domain.meter.water.reading.request.DeleteLastWaterReading
import com.ykis.mob.domain.meter.water.reading.request.GetLastWaterReading
import com.ykis.mob.domain.meter.water.reading.request.GetWaterReadings
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.meter.heat.HeatMeterState
import com.ykis.mob.ui.screens.meter.water.WaterMeterState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


class MeterViewModel (
    private val getWaterMeterList: GetWaterMeterList,
    private val getLastWaterReading: GetLastWaterReading,
    private val getWaterReadings: GetWaterReadings,
    private val addWaterReading: AddWaterReading,
    private val deleteLastWaterReadingUseCse: DeleteLastWaterReading,
    private val getHeatMeterList: GetHeatMeterList,
    private val getHeatReadings: GetHeatReadings,
    private val getLastHeatReading: GetLastHeatReading,
    private val addHeatReading : AddHeatReading,
    private val deleteLastHeatReading: DeleteLastHeatReading,
    private val logService: LogService
) : BaseViewModel(logService) {

    private val _waterMeterState = MutableStateFlow(WaterMeterState())
    val waterMeterState = _waterMeterState.asStateFlow()

    private val _heatMeterState = MutableStateFlow(HeatMeterState())
    val heatMeterState = _heatMeterState.asStateFlow()

    private val _showDetail = MutableStateFlow(false)
    val showDetail = _showDetail.asStateFlow()

    private val _contentDetail = MutableStateFlow(ContentDetail.WATER_METER)
    val contentDetail = _contentDetail.asStateFlow()

    fun getWaterMeterList(uid: String, addressId: Int) {
        this.getWaterMeterList(addressId, uid).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    this._waterMeterState.value = _waterMeterState.value.copy(
                        waterMeterList = result.data ?: emptyList(),
                        isMetersLoading = false
                    )
                }

                is Resource.Error -> {
                    this._waterMeterState.value = _waterMeterState.value.copy(
                        error = result.message ?: "Unexpected error!",
                        isMetersLoading = true
                    )
                }

                is Resource.Loading -> {
                    this._waterMeterState.value = _waterMeterState.value.copy(
                        isMetersLoading = true
                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun getHeatMeterList(uid: String, addressId: Int) {
        this.getHeatMeterList(addressId, uid).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    this._heatMeterState.value = _heatMeterState.value.copy(
                        heatMeterList = result.data ?: emptyList(),
                        isMetersLoading = false
                    )
                }

                is Resource.Error -> {
                    this._heatMeterState.value = _heatMeterState.value.copy(
                        error = result.message ?: "Unexpected error!",
                        isMetersLoading = false
                    )
                }

                is Resource.Loading -> {
                    this._heatMeterState.value = _heatMeterState.value.copy(
                        isMetersLoading = true
                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }


    fun setWaterMeterDetail(waterMeterEntity: WaterMeterEntity) {
        _waterMeterState.value = _waterMeterState.value.copy(
            selectedWaterMeter = waterMeterEntity
        )
        _contentDetail.value = ContentDetail.WATER_METER
        _showDetail.value = true
    }

    fun setHeatMeterDetail(heatMeterEntity: HeatMeterEntity) {
        _heatMeterState.value = _heatMeterState.value.copy(
            selectedHeatMeter = heatMeterEntity
        )
        _contentDetail.value = ContentDetail.HEAT_METER
        _showDetail.value = true
    }

    fun closeContentDetail() {
        _showDetail.value = false
    }

    fun getWaterReadings(uid: String, vodomerId: Int) {
        this.getWaterReadings(vodomerId, uid).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    this._waterMeterState.value = waterMeterState.value.copy(
                        waterReadings = result.data ?: emptyList(),
                        isReadingsLoading = false,
                    )
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isReadingsLoading = true
                    )
                }

                is Resource.Loading -> {
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isReadingsLoading = true
                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun getLastWaterReading(uid: String, vodomerId: Int) {
        this.getLastWaterReading(vodomerId, uid).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    this._waterMeterState.value = waterMeterState.value.copy(
                        lastWaterReading = result.data ?: WaterReadingEntity(),
                        isLastReadingLoading = false
                    )
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                }

                is Resource.Loading -> {
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = true
                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun getHeatReadings(uid: String, teplomerId: Int) {
        this.getHeatReadings(teplomerId, uid).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    this._heatMeterState.value = heatMeterState.value.copy(
                        heatReadings = result.data ?: emptyList(),
                        isReadingsLoading = false,
                    )
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isReadingsLoading = true
                    )
                }

                is Resource.Loading -> {
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isReadingsLoading = true
                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun getLastHeatReading(uid: String, teplomerId: Int) {
        this.getLastHeatReading(teplomerId, uid).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    this._heatMeterState.value = heatMeterState.value.copy(
                        lastHeatReading = result.data ?: HeatReadingEntity()
                    )
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._heatMeterState.value = heatMeterState.value.copy(
                    )
                }

                is Resource.Loading -> {
                    this._heatMeterState.value = heatMeterState.value.copy(
                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun addWaterReading(
        uid: String,
        newValue: Int,
        currentValue: Int,
        vodomerId: Int
    ) {
        this.addWaterReading(
            AddWaterReadingParams(
                uid = uid,
                newValue = newValue,
                currentValue = currentValue,
                meterId = vodomerId
            )
        ).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    SnackbarManager.showMessage("Показання додани")
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                    getLastWaterReading(uid, vodomerId)
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                }

                is Resource.Loading -> {
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = true

                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun deleteLastWaterReading(
        vodomerId: Int,
        readingId: Int,
        uid: String
    ) {
        this.deleteLastWaterReadingUseCse(
            readingId = readingId,
            uid = uid,
        ).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    SnackbarManager.showMessage("Показання видалені")
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                    getLastWaterReading(uid, vodomerId)
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                }

                is Resource.Loading -> {
                    this._waterMeterState.value = waterMeterState.value.copy(
                        isLastReadingLoading = true

                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }
    fun deleteLastHeatReading(
        teplomerId: Int,
        readingId: Int,
        uid: String
    ) {
        this.deleteLastHeatReading(
            readingId = readingId,
            uid = uid,
        ).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    SnackbarManager.showMessage("Показання видалені")
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                    getLastHeatReading(uid, teplomerId)
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                }

                is Resource.Loading -> {
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isLastReadingLoading = true

                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }
    fun addHeatReading(
        uid: String,
        newValue: Double,
        currentValue: Double,
        teplomerId: Int
    ) {
        this.addHeatReading(
            AddHeatReadingParams(
                uid = uid,
                newValue = newValue,
                currentValue = currentValue,
                meterId = teplomerId
            )
        ).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    SnackbarManager.showMessage("Показання додани")
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                    getLastHeatReading( uid,teplomerId)
                }

                is Resource.Error -> {
                    SnackbarManager.showMessage(result.resourceMessage)
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isLastReadingLoading = false
                    )
                }

                is Resource.Loading -> {
                    this._heatMeterState.value = heatMeterState.value.copy(
                        isLastReadingLoading = true

                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }
    fun onNewWaterReadingChange(newValue: String) {
        _waterMeterState.value = _waterMeterState.value.copy(
            newWaterReading = newValue
        )
    }

    fun onNewHeatReadingChange(newValue: String) {
        _heatMeterState.value = heatMeterState.value.copy(
            newHeatReading = newValue
        )
    }

    fun setContentDetail(contentDetail: ContentDetail) {
        _contentDetail.value = contentDetail
    }
}
