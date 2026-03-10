package com.ykis.mob.domain.meter.water.reading

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // Заменили @JsonClass
@Entity(tableName = "water_reading")
data class WaterReadingEntity(
  @SerialName("address_id") // Заменили @Json
  @ColumnInfo(name = "address_id")
  val addressId: Int = 0,

  @PrimaryKey(autoGenerate = false)
  @SerialName("pok_id")
  @ColumnInfo(name = "pok_id")
  val pokId: Int = 0,

  @SerialName("vodomer_id")
  @ColumnInfo(name = "vodomer_id")
  val vodomerId: Int = 0,

  @SerialName("date_ot")
  @ColumnInfo(name = "date_ot")
  val dateOt: String = "2024-01-01",

  @SerialName("date_do")
  @ColumnInfo(name = "date_do")
  val dateDo: String = "2024-01-01",

  val days: Int = 0,
  val last: Int = 0,
  val current: Int = 15,
  val kub: Int = 0,
  val avg: Byte = 0,

  @SerialName("pok_ot")
  val pokOt: Int = 0,

  @SerialName("pok_do")
  val pokDo: Int = 0,

  val rday: Int = 0,

  @SerialName("kub_day")
  val kubDay: Double = 0.0,

  @SerialName("qty_kub")
  val qtyKub: Int = 0,

  @SerialName("data_in")
  val operator: String = "Unknown",

  @SerialName("date_readings")
  val dateReadings: String = "Unknown",

  @SerialName("tarif_xv")
  val tarifXv: Double = 0.0,

  val xvoda: Double = 0.0,

  @SerialName("tarif_st")
  val tarifvSt: Double = 0.0,

  val stoki: Double = 0.0,

  @SerialName("date_st")
  val dateSt: String = "Unknown",

  @SerialName("date_fin")
  val dateFin: String = "Unknown",

  val mday: Int = 0,

  @SerialName("date_in")
  val dateIn: String = "Unknown",
)
