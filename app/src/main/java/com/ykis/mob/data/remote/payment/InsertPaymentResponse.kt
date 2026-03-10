package com.ykis.mob.data.remote.payment

import com.ykis.mob.data.remote.core.BaseResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class InsertPaymentResponse(
  @SerialName("success")
  override val success: Int,

  @SerialName("message")
  override val message: String,

  @SerialName("payment_id") // Заменили @Json на @SerialName
  val paymentId: Int = 0,

  @SerialName("uri")
  val uri: String = ""
) : BaseResponse
