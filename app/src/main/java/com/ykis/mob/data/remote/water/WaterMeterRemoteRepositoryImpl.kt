package com.ykis.mob.data.remote.water

import com.ykis.mob.core.Constants.ADDRESS_ID
import com.ykis.mob.core.Constants.CURRENT_VALUE
import com.ykis.mob.core.Constants.NEW_VALUE
import com.ykis.mob.core.Constants.POK_ID
import com.ykis.mob.core.Constants.UID
import com.ykis.mob.core.Constants.VODOMER_ID
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.api.KtorApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.water.reading.AddWaterReadingParams

class WaterMeterRemoteRepositoryImpl(
  private val ktorApiService: KtorApiService
) : WaterMeterRemoteRepository {
  override suspend fun getWaterMeterList(uid: String, addressId: Int): GetWaterMeterResponse {
    // 1. Убрали .await()
    return ktorApiService.getWaterMeterList(createGetWaterMeterMap(uid = uid, addressId = addressId))
  }

  override suspend fun getWaterReadings(uid: String, vodomerId: Int): GetWaterReadingsResponse {
    // 2. Убрали .await()
    return ktorApiService.getWaterReadings(createGetWaterReadingMap(uid, vodomerId))
  }

  override suspend fun getLastWaterReading(
    uid: String,
    vodomerId: Int,
  ): GetLastWaterReadingResponse {
    // 3. Убрали .await()
    return ktorApiService.getLastWaterReading(createGetWaterReadingMap(uid, vodomerId))
  }

  override suspend fun addWaterReading(params: AddWaterReadingParams): GetSimpleResponse {
    // 4. Убрали .await()
    return ktorApiService.addWaterReading(
      createAddNewReadingMap(
        uid = params.uid,
        vodomerId = params.meterId,
        currentValue = params.currentValue,
        newValue = params.newValue
      )
    )
  }

  override suspend fun deleteLastReading(uid: String, readingId: Int): GetSimpleResponse {
    // 5. Убрали .await()
    return ktorApiService.deleteLastWaterReading(createDeleteWaterReadingMap(uid, readingId))
  }

  private fun createGetWaterReadingMap(uid: String, vodomerId: Int): Map<String, String> {
    val map = HashMap<String, String>()
    map[VODOMER_ID] = vodomerId.toString()
    map[UID] = uid
    return map
  }

  private fun createAddNewReadingMap(
    uid: String,
    vodomerId: Int,
    newValue: Int,
    currentValue: Int
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[VODOMER_ID] = vodomerId.toString()
    map[NEW_VALUE] = newValue.toString()
    map[CURRENT_VALUE] = currentValue.toString()
    map[UID] = uid
    return map
  }

  private fun createDeleteWaterReadingMap(
    uid: String,
    pokId: Int
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[POK_ID] = pokId.toString()
    map[UID] = uid
    return map
  }

  private fun createGetWaterMeterMap(uid: String, addressId: Int): Map<String, String> {
    val map = HashMap<String, String>()
    map[ADDRESS_ID] = addressId.toString()
    map[UID] = uid
    return map
  }


}
