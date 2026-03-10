package com.ykis.mob.domain.apartment

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // Заменили @JsonClass
@Entity(tableName = "apartment")
data class ApartmentEntity(
  val uid: String? = null,

  @SerialName("address_id") // Заменили @Json
  @ColumnInfo(name = "address_id")
  @PrimaryKey
  val addressId: Int = 0,

  val address: String = "",
  val email: String = "example@email.com",
  val phone: String = "+38111111111",
  val nanim: String = "Иванов Иван Иванович",
  val order: String = "65-2020",

  @SerialName("data")
  @ColumnInfo(name = "data")
  val dataOrder: String = "1997-11-23",

  @SerialName("area_full")
  @ColumnInfo(name = "area_full")
  val areaFull: Double = 51.00,

  @SerialName("area_life")
  @ColumnInfo(name = "area_life")
  val areaLife: Double = 31.20,

  @SerialName("area_dop")
  @ColumnInfo(name = "area_dop")
  val areaDop: Double = 7.52,

  @SerialName("area_balk")
  @ColumnInfo(name = "area_balk")
  val areaBalk: Double = 4.25,

  @SerialName("area_otopl")
  @ColumnInfo(name = "area_otopl")
  val areaOtopl: Double = 51.00,

  val tenant: Int = 2,
  val podnan: Int = 0,
  val absent: Int = 1,

  @SerialName("tenant_tbo")
  @ColumnInfo(name = "tenant_tbo")
  val tenantTbo: Int = 1,

  val room: Int = 2,
  val privat: Byte = 1,
  val lift: Byte = 1,

  @SerialName("raion_id")
  @ColumnInfo(name = "block_id")
  val blockId: Int = 4,

  @SerialName("house_id")
  @ColumnInfo(name = "house_id")
  val houseId: Int = 23,

  val fio: String = "Иванов Иван Иванович",

  val subsidia: Byte = 0,
  val vxvoda: Byte = 0,
  val teplomer: Byte = 0,
  val distributor: Byte = 0,
  val kvartplata: Byte = 0,
  val otoplenie: Byte = 0,
  val ateplo: Byte = 0,
  val podogrev: Byte = 0,
  val voda: Byte = 0,
  val stoki: Byte = 0,
  val avoda: Byte = 0,
  val astoki: Byte = 0,
  val tbo: Byte = 0,

  @SerialName("aggr_kv")
  @ColumnInfo(name = "aggr_kv")
  val aggrKv: Byte = 0,

  @SerialName("aggr_voda")
  @ColumnInfo(name = "aggr_voda")
  val aggrVoda: Byte = 0,

  @SerialName("aggr_teplo")
  @ColumnInfo(name = "aggr_teplo")
  val aggrTeplo: Byte = 0,

  @SerialName("aggr_tbo")
  @ColumnInfo(name = "aggr_tbo")
  val aggrTbo: Byte = 0,

  val boiler: Byte = 0,
  val enaudit: Byte = 0,
  val heated: Byte = 0,
  val ztp: Byte = 0,
  val ovu: Byte = 0,
  val paused: Byte = 0,
  val osmd: Byte = 0,

  @SerialName("osmd_id")
  @ColumnInfo(name = "osmd_id")
  val osmdId: Int = 0,

  val osbb: String? = "Unknown",

  @SerialName("what_change")
  @ColumnInfo(name = "what_change")
  val whatChange: String = "Unknown",

  @SerialName("data_change")
  @ColumnInfo(name = "data_change")
  val dataChange: String = "Unknown",

  @SerialName("enaudit_id")
  @ColumnInfo(name = "enaudit_id")
  val enaudit_id: Int = 0,

  @SerialName("tarif_kv")
  @ColumnInfo(name = "tarif_kv")
  val tarifKv: Double = 0.00,

  @SerialName("tarif_ot")
  @ColumnInfo(name = "tarif_ot")
  val tarifOt: Double = 0.00,

  @SerialName("tarif_aot")
  @ColumnInfo(name = "tarif_aot")
  val tarifAot: Double = 0.00,

  @SerialName("tarif_gv")
  @ColumnInfo(name = "tarif_gv")
  val tarifGv: Double = 0.00,

  @SerialName("tarif_xv")
  @ColumnInfo(name = "tarif_xv")
  val tarifXv: Double = 0.00,

  @SerialName("tarif_st")
  @ColumnInfo(name = "tarif_st")
  val tarifSt: Double = 0.00,

  @SerialName("tarif_tbo")
  @ColumnInfo(name = "tarif_tbo")
  val tarifTbo: Double = 0.00,

  val tne: Double = 0.00,
  val kte: Double = 0.00,
  val length: Double = 0.00,
  val diametr: Double = 0.00,

  @SerialName("dvodomer_id")
  @ColumnInfo(name = "dvodomer_id")
  val dvodomerId: Int = 0,

  @SerialName("dteplomer_id")
  @ColumnInfo(name = "dteplomer_id")
  val dteplomerId: Int = 0,

  val operator: String = "Unknown",

  @SerialName("data_in")
  @ColumnInfo(name = "data_in")
  val dataIn: String = "Unknown",

  val ipay: Int = 0,
  val pb: Int = 0,
  val mtb: Int = 0
)
