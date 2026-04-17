package com.ykis.mob.data.remote.service

import android.util.Log
import com.ykis.mob.core.Constants.ADDRESS_ID
import com.ykis.mob.core.Constants.HOUSE_ID
import com.ykis.mob.core.Constants.SERVICE
import com.ykis.mob.core.Constants.TOTAL
import com.ykis.mob.core.Constants.UID
import com.ykis.mob.core.Constants.YEAR
import com.ykis.mob.data.remote.api.KtorApiService
import com.ykis.mob.domain.service.request.ServiceParams

class ServiceRemoteImpl(
  private val ktorApiService: KtorApiService
) : ServiceRemote {

  override suspend fun getFlatDetailServices(params: ServiceParams): GetServiceResponse {
    val methodName = "ServiceRemote.getFlatDetail"
    val map = createGetFlatServiceMap(
      params.uid, params.addressId, params.houseId, params.year, params.service, params.total
    )

    Log.d("YkisLog", "$methodName: [SEND] Params: $map")

    return try {
      val response = ktorApiService.getFlatService(map)
      Log.d("YkisLog", "$methodName: [RECV] Success: ${response.success}, Count: ${response.services.size}")
      response
    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      throw e
    }
  }

  override suspend fun getTotalDebtService(params: ServiceParams): GetServiceResponse {
    val methodName = "ServiceRemote.getTotalDebt"
    val map = createGetFlatServiceMap(
      params.uid, params.addressId, params.houseId, params.year, params.service, params.total
    )

    Log.d("YkisLog", "$methodName: [SEND] Params: $map")

    return try {
      val response = ktorApiService.getFlatService(map)
      Log.d("YkisLog", "$methodName: [RECV] Success: ${response.success}, Debt: ${response.services.firstOrNull()?.dolg}")
      response
    } catch (e: Exception) {
      Log.e("YkisLog", "$methodName: [ERROR] ${e.message}")
      throw e
    }
  }

  private fun createGetFlatServiceMap(
    uid: String,
    addressId: Int,
    houseId: Int,
    year: String,
    service: Byte,
    total: Byte,
  ): Map<String, String> {
    return mapOf(
      UID to uid,
      ADDRESS_ID to addressId.toString(),
      HOUSE_ID to houseId.toString(),
      YEAR to year,
      SERVICE to service.toString(),
      TOTAL to total.toString()
    )
  }
}

