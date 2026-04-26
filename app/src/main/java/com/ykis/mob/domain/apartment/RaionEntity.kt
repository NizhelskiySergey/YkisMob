package com.ykis.mob.domain.apartment

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
@Entity(tableName = "raion")
data class RaionEntity(
 @SerialName("raionId")
 @ColumnInfo(name = "raionId")
 @PrimaryKey
  val raionId: Int = 0, // Убедись, что библиотека умеет парсить "1" в 1, или смени на String

  @SerialName("raion")
  val raion: String = ""
)
