package com.ykis.mob.domain.meter.heat.meter

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // Заменили Moshi на Kotlin Serialization
@Entity(tableName = "heat_meter")
data class HeatMeterEntity(
  @PrimaryKey(autoGenerate = false)
  @SerialName("teplomer_id") // Заменили @Json
  @ColumnInfo(name = "teplomer_id")
  val teplomerId: Int = 0,

  @SerialName("nomer")
  val number: String = "Unknown",

  val model: String = "Unknown",

  @SerialName("model_id")
  @ColumnInfo(name = "model_id")
  val modelId: Int = 0,

  @SerialName("address_id")
  @ColumnInfo(name = "address_id")
  val addressId: Int = 0,

  val edizm: String = "Unknown",
  val koef: String = "Unknown",
  val area: Double = 0.0,
  val sdate: String = "Unknown",
  val fpdate: String = "Unknown",
  val pdate: String = "Unknown",
  val out: Byte = 0,
  val spisan: Byte = 0,

  @SerialName("data_spis")
  @ColumnInfo(name = "data_spis")
  val dataSpis: String = "Unknown",

  val work: Byte = 0
)
