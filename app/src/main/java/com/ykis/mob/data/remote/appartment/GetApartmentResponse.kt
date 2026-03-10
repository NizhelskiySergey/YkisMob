package com.ykis.mob.data.remote.appartment

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GetApartmentResponse(
  @SerialName("success")
  override val success: Int,

  @SerialName("message")
  override val message: String,

  @SerialName("apartment")
  val apartment: ApartmentEntity? = null
) : BaseResponse
