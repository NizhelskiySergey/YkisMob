package com.ykis.mob.domain

import com.ykis.mob.ui.navigation.ContentDetail
enum class UserRole(val codeName: ContentDetail) {
  StandardUser(ContentDetail.STANDARD_USER),
  VodokanalUser(ContentDetail.WATER_SERVICE),
  YtkeUser(ContentDetail.WARM_SERVICE),
  TboUser(ContentDetail.GARBAGE_SERVICE),
  OsbbUser(ContentDetail.OSBB);

  companion object {
    fun fromString(roleStr: String?): UserRole {
      // Ищем совпадение по имени константы или по строке из БД
      return entries.find { it.name.equals(roleStr, ignoreCase = true) }
        ?: entries.find { it.codeName.name.equals(roleStr, ignoreCase = true) }
        ?: StandardUser
    }
  }
}

