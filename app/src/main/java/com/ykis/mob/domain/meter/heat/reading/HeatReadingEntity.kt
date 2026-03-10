package com.ykis.mob.domain.meter.heat.reading

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // Заменили Moshi на Kotlin Serialization
@Entity(tableName = "heat_reading")
data class HeatReadingEntity(
  @SerialName("address_id") // Заменили @Json
  @ColumnInfo(name = "address_id")
  val addressId: Int = 0,

  @PrimaryKey(autoGenerate = false)
  @SerialName("pok_id")
  @ColumnInfo(name = "pok_id")
  val pokId: Int = 0,

  @SerialName("teplomer_id")
  @ColumnInfo(name = "teplomer_id")
  val teplomerId: Int = 0,

  @SerialName("date_readings")
  @ColumnInfo(name = "date_reading")
  val dateReading: String = "Unknown",

  @SerialName("date_ot")
  @ColumnInfo(name = "date_ot")
  val dateOt: String = "2024-01-01",

  @SerialName("date_do")
  @ColumnInfo(name = "date_do")
  val dateDo: String = "2024-01-01",

  val edizm: String = "Unknown",
  val koef: String = "Unknown",
  val days: Short = 0,
  val last: Double = 0.0,
  val current: Double = 0.0,
  val gkal: Double = 0.0,
  val avg: Byte = 0,
  val tarif: Double = 0.0,
  val qty: Double = 0.0,

  @SerialName("pok_ot")
  @ColumnInfo(name = "pok_ot")
  val pokOt: String = "Unknown",

  @SerialName("pok_do")
  @ColumnInfo(name = "pok_do")
  val pokDo: String = "Unknown",

  @SerialName("gkal_rasch")
  @ColumnInfo(name = "gkal_rasch")
  val gkalRasch: String = "Unknown",

  @SerialName("gkal_day")
  @ColumnInfo(name = "gkal_day")
  val gkalDay: String = "Unknown",

  @SerialName("qty_day")
  @ColumnInfo(name = "qty_day")
  val qtyDay: String = "Unknown",

  @SerialName("day_avg")
  @ColumnInfo(name = "day_avg")
  val dayAvg: String = "Unknown",

  @SerialName("data_in")
  @ColumnInfo(name = "date_in")
  val dateIn: String = "Unknown",

  val operator: String = "Unknown"
)
