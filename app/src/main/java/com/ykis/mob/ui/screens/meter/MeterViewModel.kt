package com.ykis.mob.ui.screens.meter

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.domain.meter.heat.meter.HeatMeterEntity
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity
import com.ykis.mob.domain.meter.water.meter.WaterMeterEntity
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.meter.heat.HeatMeterState
import com.ykis.mob.ui.screens.meter.water.WaterMeterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MeterViewModel (
  private val waterMeterRepository: WaterMeterRepository,
  private val heatMeterRepository: HeatMeterRepository,
  logService: LogService

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
    viewModelScope.launch {
      _waterMeterState.value = _waterMeterState.value.copy(isMetersLoading = true)
      try {
        // Прямой вызов репозитория (Resolution Time упадет до 2-5 мс)
        val response = waterMeterRepository.getWaterMeterList( uid,addressId)

        // ИСПОЛЬЗУЕМ ВАШЕ ПОЛЕ waterMeters
        val metersList = response.waterMeters ?: emptyList()

        _waterMeterState.value = _waterMeterState.value.copy(
          waterMeterList = metersList,
          isMetersLoading = false
        )
      } catch (e: Exception) {
        _waterMeterState.value = _waterMeterState.value.copy(
          error = e.message ?: "Ошибка сети",
          isMetersLoading = false
        )
      }
    }
  }



  fun getHeatMeterList(uid: String, addressId: Int) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки
      _heatMeterState.value = _heatMeterState.value.copy(isMetersLoading = true)

      try {
        // 2. Прямой вызов репозитория (Resolution Time упадет до 2-5 мс)
        val response = heatMeterRepository.getHeatMeterList(uid,addressId)

        // 3. Обновляем состояние (замените .heatMeters на ваше поле из ответа)
        _heatMeterState.value = _heatMeterState.value.copy(
          heatMeterList = response.heatMeters ?: emptyList(),
          isMetersLoading = false
        )
      } catch (e: Exception) {
        // 4. Обработка ошибки
        Log.e("MeterViewModel", "Error fetching heat meters: ${e.message}")
        _heatMeterState.value = _heatMeterState.value.copy(
          error = e.message ?: "Unexpected error!",
          isMetersLoading = false
        )
      }
    }
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
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки через update (атомарно)
      _waterMeterState.update { it.copy(isReadingsLoading = true) }

      try {
        // 2. Выполняем запрос в IO потоке
        val response = withContext(Dispatchers.IO) {
          waterMeterRepository.getWaterReadings(uid, vodomerId)
        }

        // 3. Обновляем состояние данными (Ktor уже распарсил JSON)
        _waterMeterState.update { state ->
          state.copy(
            waterReadings = response.waterReadings, // Поле уже проинициализировано как emptyList() в модели
            isReadingsLoading = false
          )
        }
      } catch (e: Exception) {
        // 4. Обработка ошибок (сеть, 404, ошибки парсинга)
        Log.e("MeterViewModel", "Error fetching readings: ${e.message}")
        SnackbarManager.showMessage("Ошибка загрузки показаний")

        _waterMeterState.update { it.copy(isReadingsLoading = false) }
      }
    }
  }



  fun getLastWaterReading(uid: String, vodomerId: Int) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки
      _waterMeterState.value = _waterMeterState.value.copy(isLastReadingLoading = true)

      try {
        // 2. Прямой вызов репозитория (Resolution Time упадет до 2-5 мс)
        val response = waterMeterRepository.getLastWaterReading(uid,vodomerId)

        // 3. Обновляем состояние (замените .waterReading на актуальное поле вашего ответа)
        _waterMeterState.value = _waterMeterState.value.copy(
          lastWaterReading = response.waterReading ?: WaterReadingEntity(),
          isLastReadingLoading = false
        )
      } catch (e: Exception) {
        // 4. Обработка ошибки
        Log.e("MeterViewModel", "Error fetching last reading: ${e.message}")
        SnackbarManager.showMessage("Ошибка получения последнего показания")

        _waterMeterState.value = _waterMeterState.value.copy(
          isLastReadingLoading = false
        )
      }
    }
  }


  fun getHeatReadings(uid: String, teplomerId: Int) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки
      _heatMeterState.value = _heatMeterState.value.copy(isReadingsLoading = true)

      try {
        // 2. Прямой вызов репозитория (вместо UseCase)
        val response = heatMeterRepository.getHeatReadings(uid,teplomerId)

        // 3. Обновляем состояние (замените .heatReadings на ваше поле из ответа)
        _heatMeterState.value = _heatMeterState.value.copy(
          heatReadings = response.heatReadings ?: emptyList(),
          isReadingsLoading = false
        )
      } catch (e: Exception) {
        // 4. Обработка ошибки
        Log.e("MeterViewModel", "Error fetching heat readings: ${e.message}")
        SnackbarManager.showMessage("Ошибка загрузки показаний тепла")

        _heatMeterState.value = _heatMeterState.value.copy(
          isReadingsLoading = false // Выключаем лоадер при ошибке
        )
      }
    }
  }


  fun getLastHeatReading(uid: String, teplomerId: Int) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки (если нужно добавить в стейт флаг isLastReadingLoading)
      _heatMeterState.value = _heatMeterState.value.copy(isReadingsLoading = true)

      try {
        // 2. Прямой вызов репозитория (Resolution Time упадет до минимума)
        val response = heatMeterRepository.getLastHeatReading(uid,teplomerId)

        // 3. Обновляем состояние (замените .heatReading на актуальное поле вашего ответа)
        _heatMeterState.value = _heatMeterState.value.copy(
          lastHeatReading = response.heatReading ?: HeatReadingEntity(),
          isReadingsLoading = false
        )
      } catch (e: Exception) {
        // 4. Обработка ошибки
        Log.e("MeterViewModel", "Error fetching last heat reading: ${e.message}")
        SnackbarManager.showMessage("Ошибка получения последнего показания тепла")

        _heatMeterState.value = _heatMeterState.value.copy(
          isReadingsLoading = false
        )
      }
    }
  }


  fun addWaterReading(
    uid: String,
    newValue: Int,
    currentValue: Int,
    vodomerId: Int
  ) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки
      _waterMeterState.value = _waterMeterState.value.copy(isLastReadingLoading = true)

      try {
        // 2. Прямой вызов репозитория с параметрами (без UseCase)
        val params = AddWaterReadingParams(
          uid = uid,
          newValue = newValue,
          currentValue = currentValue,
          meterId = vodomerId
        )
        val response = waterMeterRepository.addWaterReading(params)

        // 3. Проверяем успех (замените .success на ваше поле из ответа)
        if (response.success == 1) {
          SnackbarManager.showMessage("Показання додані")
          // Обновляем данные после успешного добавления
          getLastWaterReading(uid, vodomerId)
        } else {
          SnackbarManager.showMessage(response.message ?: "Помилка додавання")
        }

        _waterMeterState.value = _waterMeterState.value.copy(isLastReadingLoading = false)

      } catch (e: Exception) {
        // 4. Обработка сетевой ошибки
        Log.e("MeterViewModel", "Add reading error: ${e.message}")
        SnackbarManager.showMessage("Помилка зв'язку с сервером")
        _waterMeterState.value = _waterMeterState.value.copy(isLastReadingLoading = false)
      }
    }
  }


  fun deleteLastWaterReading(
    uid: String,
    vodomerId: Int,
    readingId: Int,
  ) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки
      _waterMeterState.value = _waterMeterState.value.copy(isLastReadingLoading = true)

      try {
        // 2. Прямой вызов репозитория (убираем deleteLastWaterReadingUseCse)
        val response = waterMeterRepository.deleteLastWaterReading(uid,readingId)

        // 3. Проверяем успех (предполагаем поле .success == 1 из BaseResponse)
        if (response.success == 1) {
          SnackbarManager.showMessage("Показання видалені")
          // 4. Обновляем данные после успешного удаления
          getLastWaterReading(uid, vodomerId)
        } else {
          SnackbarManager.showMessage(response.message ?: "Помилка видалення")
        }

        _waterMeterState.value = _waterMeterState.value.copy(isLastReadingLoading = false)

      } catch (e: Exception) {
        // 5. Обработка сетевой ошибки
        Log.e("MeterViewModel", "Delete error: ${e.message}")
        SnackbarManager.showMessage("Помилка зв'язку с сервером")
        _waterMeterState.value = _waterMeterState.value.copy(isLastReadingLoading = false)
      }
    }
  }

  fun deleteLastHeatReading(
    uid: String,
    teplomerId: Int,
    readingId: Int,
  ) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки
      _heatMeterState.value = _heatMeterState.value.copy(isLastReadingLoading = true)

      try {
        // 2. Прямой вызов репозитория (Resolution Time упадет до минимума)
        val response = heatMeterRepository.deleteLastHeatReading(uid,readingId)

        // 3. Проверяем успех (предполагаем поле .success == 1 из ответа)
        if (response.success == 1) {
          SnackbarManager.showMessage("Показання видалені")
          // 4. Обновляем данные после успешного удаления
          getLastHeatReading(uid, teplomerId)
        } else {
          SnackbarManager.showMessage(response.message ?: "Помилка видалення")
        }

        _heatMeterState.value = _heatMeterState.value.copy(isLastReadingLoading = false)

      } catch (e: Exception) {
        // 5. Обработка сетевой ошибки
        Log.e("MeterViewModel", "Delete heat error: ${e.message}")
        SnackbarManager.showMessage("Помилка зв'язку с сервером")
        _heatMeterState.value = _heatMeterState.value.copy(isLastReadingLoading = false)
      }
    }
  }

  fun addHeatReading(
    uid: String,
    newValue: Double,
    currentValue: Double,
    teplomerId: Int
  ) {
    viewModelScope.launch {
      // 1. Включаем индикатор загрузки
      _heatMeterState.value = _heatMeterState.value.copy(isLastReadingLoading = true)

      try {
        // 2. Прямой вызов репозитория (вместо UseCase)
        val params = AddHeatReadingParams(
          uid = uid,
          newValue = newValue,
          currentValue = currentValue,
          meterId = teplomerId
        )
        val response = heatMeterRepository.addHeatReading(params)

        // 3. Проверяем успех (предполагаем поле .success == 1)
        if (response.success == 1) {
          SnackbarManager.showMessage("Показання додані")
          // Обновляем данные после успешного добавления
          getLastHeatReading(uid, teplomerId)
        } else {
          SnackbarManager.showMessage(response.message ?: "Помилка додавания")
        }

        _heatMeterState.value = _heatMeterState.value.copy(isLastReadingLoading = false)

      } catch (e: Exception) {
        // 4. Обработка ошибки
        Log.e("MeterViewModel", "Add heat reading error: ${e.message}")
        SnackbarManager.showMessage("Помилка зв'язку с сервером")
        _heatMeterState.value = _heatMeterState.value.copy(isLastReadingLoading = false)
      }
    }
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
