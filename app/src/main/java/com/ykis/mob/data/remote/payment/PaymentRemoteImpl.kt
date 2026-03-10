package com.ykis.mob.data.remote.payment

import com.ykis.mob.core.Constants.ADDRESS_ID
import com.ykis.mob.core.Constants.KVARTPLATA
import com.ykis.mob.core.Constants.RFOND
import com.ykis.mob.core.Constants.TBO
import com.ykis.mob.core.Constants.TEPLO
import com.ykis.mob.core.Constants.UID
import com.ykis.mob.core.Constants.VODA
import com.ykis.mob.core.Constants.YEAR
import com.ykis.mob.data.remote.api.KtorApiService
import com.ykis.mob.domain.payment.request.InsertPaymentParams

class PaymentRemoteImpl (
    private val ktorApiService: KtorApiService
) : PaymentRemote {

  override suspend fun getPaymentList(addressId: Int, year: String, uid: String): GetPaymentResponse {
    // Убрали .await(), KtorApiService сразу возвращает GetPaymentResponse
    return ktorApiService.getFlatPayment(
      createGetPaymentListMap(
        addressId, year, uid
      )
    )
  }

  override suspend fun insertPayment(params: InsertPaymentParams): InsertPaymentResponse {
    // Убрали .await(), KtorApiService сразу возвращает InsertPaymentResponse
    return ktorApiService.insertPayment(
      createInsertPaymentMap(params)
    )
  }


  private fun createGetPaymentListMap(
        addressId: Int,
        year: String,
        uid: String
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[ADDRESS_ID] = addressId.toString()
        map[YEAR] = year
        map[UID] = uid
        return map
    }

    private fun createInsertPaymentMap(
        params: InsertPaymentParams
    ): Map<String, String> {
        val map = HashMap<String, String>()
        map[UID] = params.uid
        map[ADDRESS_ID] = params.addressId.toString()
        map[KVARTPLATA] = params.kvartplata.toString()
        map[RFOND] = params.rfond.toString()
        map[TEPLO] = params.teplo.toString()
        map[VODA] = params.voda.toString()
        map[TBO] = params.tbo.toString()

        return map
    }
}
