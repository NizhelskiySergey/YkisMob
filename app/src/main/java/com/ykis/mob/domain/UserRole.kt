package com.ykis.mob.domain

import com.ykis.mob.ui.navigation.ContentDetail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // Обязательно добавь аннотацию
enum class UserRole(val codeName: ContentDetail) {
  @SerialName("STANDARD_USER")
  StandardUser(ContentDetail.STANDARD_USER),

  @SerialName("WATER_SERVICE")
  VodokanalUser(ContentDetail.WATER_SERVICE),

  @SerialName("WARM_SERVICE")
  YtkeUser(ContentDetail.WARM_SERVICE),

  @SerialName("GARBAGE_SERVICE")
  TboUser(ContentDetail.GARBAGE_SERVICE),

  @SerialName("OSBB") // Теперь "OSBB" из JSON превратится в OsbbUser
  OsbbUser(ContentDetail.OSBB);

  companion object {
    fun fromString(roleStr: String?): UserRole {
      if (roleStr == null) return StandardUser
      return entries.find { it.name.equals(roleStr, ignoreCase = true) }
        ?: entries.find { it.codeName.name.equals(roleStr, ignoreCase = true) }
        ?: StandardUser
    }
  }
}


