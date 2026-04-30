package com.ykis.mob.domain.apartment

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "houses")
data class HouseEntity(
  @SerialName("houseId")
  @ColumnInfo(name = "houseId")
  @PrimaryKey
  val houseId: Int = 0,
  @SerialName("raionId")
  @ColumnInfo(name = "raionId")
  val raionId: Int = 0,
  @SerialName("house")
  val house: String = ""
)
