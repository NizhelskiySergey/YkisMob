package com.ykis.mob.data.remote.family

import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.family.FamilyEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GetFamilyResponse(
  @SerialName("success")
  override val success: Int,

  @SerialName("message")
  override val message: String,

  // Поле из JSON от PHP обычно называется 'family'
  @SerialName("family")
  val family: List<FamilyEntity> = emptyList()
) : BaseResponse
