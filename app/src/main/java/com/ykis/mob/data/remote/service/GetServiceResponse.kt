package com.ykis.mob.data.remote.service

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.service.ServiceEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GetServiceResponse(
  @SerialName("success")
  override val success: Int,

  @SerialName("message")
  override val message: String,

  // В JSON от PHP поле со списком услуг
  @SerialName("services")
  val services: List<ServiceEntity> = emptyList()
) : BaseResponse
