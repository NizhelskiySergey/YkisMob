package com.ykis.mob.data.remote

import com.ykis.mob.data.remote.core.BaseResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GetSimpleResponse(
  @SerialName("success") override val success: Int = 0,
  @SerialName("message") override val message: String = "",
  @SerialName("address_id") val addressId: Int = 0
) : BaseResponse
