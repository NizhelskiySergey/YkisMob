package com.ykis.mob.data.remote.heat

import com.ykis.mob.core.Constants.ADDRESS_ID
import com.ykis.mob.core.Constants.CURRENT_VALUE
import com.ykis.mob.core.Constants.NEW_VALUE
import com.ykis.mob.core.Constants.POK_ID
import com.ykis.mob.core.Constants.TEPLOMER_ID
import com.ykis.mob.core.Constants.UID
import com.ykis.mob.data.remote.GetSimpleResponse
import com.ykis.mob.data.remote.api.KtorApiService
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.reading.AddHeatReadingParams

class HeatMeterRemoteRepositoryImpl (
    private val ktorApiService: KtorApiService
) : HeatMeterRemoteRepository {
  override suspend fun getHeatMeterList(uid: String, addressId: Int): GetHeatMeterResponse {
    // Просто возвращаем результат вызова
    return ktorApiService.getHeatMeterList(createGetHeatMeterMap(uid, addressId))
  }

  override suspend fun getHeatReadings(uid: String, teplomerId: Int): GetHeatReadingResponse {
    return ktorApiService.getHeatReadings(
      createGetHeatReadingMap(uid, teplomerId)
    )
  }

  override suspend fun getLastHeatReading(
    uid: String,
    teplomerId: Int
  ): GetLastHeatReadingResponse {
    return ktorApiService.getLastHeatReading(
      createGetHeatReadingMap(uid, teplomerId)
    )
  }

  override suspend fun addHeatReading(params: AddHeatReadingParams): GetSimpleResponse {
    return ktorApiService.addHeatReading(
      createAddReadingMap(
        uid = params.uid,
        teplomerId = params.meterId,
        currentValue = params.currentValue,
        newValue = params.newValue
      )
    )
  }

  override suspend fun deleteLastHeatReading(uid: String, readingId: Int): GetSimpleResponse {
    return ktorApiService.deleteLastHeatReading(
      createDeleteWaterReadingMap(uid, readingId)
    )
  }


  private fun createGetHeatReadingMap(
    uid: String,
    teplomerId: Int,
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[TEPLOMER_ID] = teplomerId.toString()
    map[UID] = uid
    return map
  }

  private fun createAddReadingMap(
    uid: String,
    teplomerId: Int,
    newValue: Double,
    currentValue: Double,
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[TEPLOMER_ID] = teplomerId.toString()
    map[NEW_VALUE] = newValue.toString()
    map[CURRENT_VALUE] = currentValue.toString()
    map[UID] = uid
    return map
  }

  private fun createDeleteWaterReadingMap(
    uid: String,
    pokId: Int,
  ): Map<String, String> {
    val map = HashMap<String, String>()
    map[POK_ID] = pokId.toString()
    map[UID] = uid
    return map
  }

    private fun createGetHeatMeterMap(uid: String,addressId: Int,): Map<String, String> {
        val map = HashMap<String, String>()
        map[ADDRESS_ID] = addressId.toString()
        map[UID] = uid
        return map
    }
}
