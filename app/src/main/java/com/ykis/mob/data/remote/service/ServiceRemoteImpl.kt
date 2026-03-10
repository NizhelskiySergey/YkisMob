package com.ykis.mob.data.remote.service

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
    // 1. Убрали .await() — Ktor сразу возвращает GetServiceResponse
    return ktorApiService.getFlatService(
      createGetFlatServiceMap(
        params.uid,
        params.addressId,
        params.houseId,
        params.year,
        params.service,
        params.total
      )
    )
  }

  override suspend fun getTotalDebtService(params: ServiceParams): GetServiceResponse {
    // 2. Аналогично убираем .await()
    return ktorApiService.getFlatService(
      createGetFlatServiceMap(
        params.uid,
        params.addressId,
        params.houseId,
        params.year,
        params.service,
        params.total
      )
    )
  }


  private fun createGetFlatServiceMap(
        uid: String,
        addressId: Int,
        houseId: Int,
        year: String,
        service: Byte,
        total: Byte,
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[UID] = uid
        map[ADDRESS_ID] = addressId.toString()
        map[HOUSE_ID] = houseId.toString()
        map[YEAR] = year
        map[SERVICE] = service.toString()
        map[TOTAL] = total.toString()
        return map
    }

}
