package com.ykis.mob.data


import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.data.remote.heat.GetHeatMeterResponse
import com.ykis.mob.data.remote.heat.GetHeatReadingResponse
import com.ykis.mob.data.remote.heat.GetLastHeatReadingResponse
import com.ykis.mob.data.remote.heat.HeatMeterRemoteRepository
import com.ykis.mob.domain.meter.heat.meter.HeatMeterRepository
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams

class HeatMeterRepositoryImpl (
  private val heatMeterRemoteRepository: HeatMeterRemoteRepository,
) : HeatMeterRepository {
    override suspend fun getHeatMeterList(uid: String,addressId: Int): GetHeatMeterResponse {
        return heatMeterRemoteRepository.getHeatMeterList(uid,addressId)
    }

  override suspend fun getHeatReadings(uid: String,teplomerId: Int): GetHeatReadingResponse {
    return heatMeterRemoteRepository.getHeatReadings(uid,teplomerId)
  }

  override suspend fun getLastHeatReading(uid: String,teplomerId: Int): GetLastHeatReadingResponse {
    return heatMeterRemoteRepository.getLastHeatReading(uid,teplomerId)
  }

  override suspend fun addHeatReading(params: AddHeatReadingParams): BaseResponse {
    return heatMeterRemoteRepository.addHeatReading(params)
  }

  override suspend fun deleteLastHeatReading(uid: String,readingId: Int): BaseResponse {
    return heatMeterRemoteRepository.deleteLastHeatReading(uid,readingId)
  }
}
