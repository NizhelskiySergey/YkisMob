package com.ykis.mob.data.remote.payment

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.payment.PaymentEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GetPaymentResponse(
  @SerialName("success")
  override val success: Int,

  @SerialName("message")
  override val message: String,

  // В JSON от PHP поле обычно называется 'payments'
  @SerialName("payments")
  val payments: List<PaymentEntity> = emptyList()
) : BaseResponse
