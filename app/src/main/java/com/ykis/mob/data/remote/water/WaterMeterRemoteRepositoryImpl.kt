package com.ykis.mob.data.remote.water

import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.api.ApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams
import retrofit2.await

class WaterMeterRemoteRepositoryImpl(
  private val apiService: ApiService
) : WaterMeterRemoteRepository {

  override suspend fun getWaterMeterList(uid: String, addressId: Int): GetWaterMeterResponse {
    return apiService.getWaterMeterList(createGetWaterMeterMap(uid = uid, addressId = addressId))
      .await()
  }

  override suspend fun getWaterReadings(uid: String, vodomerId: Int): GetWaterReadingsResponse {
    return apiService.getWaterReadings(
      createGetWaterReadingMap(uid, vodomerId)
    ).await()
  }

  override suspend fun getLastWaterReading(
    uid: String,
    vodomerId: Int,
  ): GetLastWaterReadingResponse {
    return apiService.getLastWaterReading(
      createGetWaterReadingMap(uid, vodomerId)
    ).await()
  }

  override suspend fun addWaterReading(params: AddWaterReadingParams): GetSimpleResponse {
    return apiService.addWaterReading(

      createAddNewReadingMap(
        uid = params.uid,
        vodomerId = params.meterId,
        currentValue = params.currentValue,
        newValue = params.newValue
      )
    ).await()
  }

  override suspend fun deleteLastReading(uid: String, readingId: Int): BaseResponse {
    return apiService.deleteLastWaterReading(
      createDeleteWaterReadingMap(uid, readingId)
    ).await()
  }

  private fun createGetWaterReadingMap(uid: String, vodomerId: Int): Map<String, String> {
    val map = HashMap<String, String>()
    map[ApiService.VODOMER_ID] = vodomerId.toString()
    map[ApiService.UID] = uid
    return map
  }

  private fun createAddNewReadingMap(
    uid: String,
    vodomerId: Int,
    newValue: Int,
    currentValue: Int
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[ApiService.VODOMER_ID] = vodomerId.toString()
    map[ApiService.NEW_VALUE] = newValue.toString()
    map[ApiService.CURRENT_VALUE] = currentValue.toString()
    map[ApiService.UID] = uid
    return map
  }

  private fun createDeleteWaterReadingMap(
    uid: String,
    pokId: Int
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[ApiService.POK_ID] = pokId.toString()
    map[ApiService.UID] = uid
    return map
  }

  private fun createGetWaterMeterMap(uid: String, addressId: Int): Map<String, String> {
    val map = HashMap<String, String>()
    map[ApiService.ADDRESS_ID] = addressId.toString()
    map[ApiService.UID] = uid
    return map
  }


}
