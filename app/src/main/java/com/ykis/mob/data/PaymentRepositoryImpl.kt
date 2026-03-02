package com.ykis.mob.data

import com.ykis.mob.data.remote.payment.GetPaymentResponse
import com.ykis.mob.data.remote.payment.InsertPaymentResponse
import com.ykis.mob.data.remote.payment.PaymentRemote
import com.ykis.mob.domain.payment.PaymentRepository
import com.ykis.mob.domain.payment.request.InsertPaymentParams

class PaymentRepositoryImpl (
    private val paymentRemote: PaymentRemote,
) : PaymentRepository {
    override suspend fun getPaymentList(addressId: Int, year: String, uid: String): GetPaymentResponse {
        return paymentRemote.getPaymentList(addressId , year, uid)
    }

    override suspend fun insertPayment(params: InsertPaymentParams): InsertPaymentResponse {
        return paymentRemote.insertPayment(params)
    }

}
