package com.ykis.mob.data.remote.appartment
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.RaionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GetRaionsResponse(
  @SerialName("success")
  override val success: Int,

  @SerialName("message")
  override val message: String,

  @SerialName("raions")
    val raions: List<RaionEntity> = emptyList()
) : BaseResponse
