package com.ykis.mob.data

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.data.remote.water.GetLastWaterReadingResponse
import com.ykis.mob.data.remote.water.GetWaterMeterResponse
import com.ykis.mob.data.remote.water.GetWaterReadingsResponse
import com.ykis.mob.data.remote.water.WaterMeterRemoteRepository
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams

class WaterMeterRepositoryImpl (
  private val waterMeterRemoteRepository: WaterMeterRemoteRepository,
) : WaterMeterRepository {
    override suspend fun getWaterMeterList(uid: String,addressId: Int ): GetWaterMeterResponse {
        return waterMeterRemoteRepository.getWaterMeterList(uid,addressId,)
    }
  override suspend fun getWaterReadings(uid: String,vodomerId: Int): GetWaterReadingsResponse {
    return waterMeterRemoteRepository.getWaterReadings(uid,vodomerId)
  }

  override suspend fun getLastWaterReading(uid: String,vodomerId: Int): GetLastWaterReadingResponse {
    return waterMeterRemoteRepository.getLastWaterReading(uid,vodomerId)
  }

  override suspend fun addWaterReading(params: AddWaterReadingParams): GetSimpleResponse {
    return waterMeterRemoteRepository.addWaterReading(params)
  }

  override suspend fun deleteLastWaterReading(uid: String, readingId: Int ): GetSimpleResponse {
    return waterMeterRemoteRepository.deleteLastReading(uid,readingId )
  }
}
