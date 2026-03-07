package com.ykis.mob.data.remote.heat

import com.ykis.mob.data.remote.api.ApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams
import retrofit2.await

class HeatMeterRemoteRepositoryImpl (
    private val apiService: ApiService
) : HeatMeterRemoteRepository {

    override suspend fun getHeatMeterList(uid: String, addressId: Int): GetHeatMeterResponse {
        return apiService.getHeatMeterList(createGetHeatMeterMap(uid,addressId)).await()
    }
  override suspend fun getHeatReadings(uid: String, teplomerId: Int): GetHeatReadingResponse {
    return apiService.getHeatReadings(
      createGetHeatReadingMap(uid, teplomerId)
    ).await()
  }

  override suspend fun getLastHeatReading(
    uid: String,
    teplomerId: Int
  ): GetLastHeatReadingResponse {
    return apiService.getLastHeatReading(
      createGetHeatReadingMap(uid, teplomerId)
    ).await()
  }

  override suspend fun addHeatReading(params: AddHeatReadingParams): BaseResponse {
    return apiService.addHeatReading(
      createAddReadingMap(
        uid = params.uid,
        teplomerId = params.meterId,
        currentValue = params.currentValue,
        newValue = params.newValue,

        )
    ).await()
  }

  override suspend fun deleteLastHeatReading(uid: String, readingId: Int): BaseResponse {
    return apiService.deleteLastHeatReading(
      createDeleteWaterReadingMap(uid,  readingId)
    ).await()
  }

  private fun createGetHeatReadingMap(
    uid: String,
    teplomerId: Int,
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[ApiService.TEPLOMER_ID] = teplomerId.toString()
    map[ApiService.UID] = uid
    return map
  }

  private fun createAddReadingMap(
    uid: String,
    teplomerId: Int,
    newValue: Double,
    currentValue: Double,
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[ApiService.TEPLOMER_ID] = teplomerId.toString()
    map[ApiService.NEW_VALUE] = newValue.toString()
    map[ApiService.CURRENT_VALUE] = currentValue.toString()
    map[ApiService.UID] = uid
    return map
  }

  private fun createDeleteWaterReadingMap(
    uid: String,
    pokId: Int,
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[ApiService.POK_ID] = pokId.toString()
    map[ApiService.UID] = uid
    return map
  }

    private fun createGetHeatMeterMap(uid: String,addressId: Int,): Map<String, String> {
        val map = HashMap<String, String>()
        map[ApiService.ADDRESS_ID] = addressId.toString()
        map[ApiService.UID] = uid
        return map
    }
}
