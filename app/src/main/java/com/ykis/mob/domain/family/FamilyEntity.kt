package com.ykis.mob.domain.family

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // Заменили Moshi на Kotlin Serialization
@Entity(tableName = "family")
data class FamilyEntity(
  @PrimaryKey(autoGenerate = false)
  @SerialName("rec_id") // Заменили @Json
  @ColumnInfo(name = "rec_id")
  val recId: Int = 0,

  @SerialName("address_id")
  @ColumnInfo(name = "address_id")
  val addressId: Int = 0,

  val rodstvo: String = "Unknown",

  @SerialName("firstname")
  @ColumnInfo(name = "firstname")
  val fistname: String = "Unknown",

  @SerialName("lastname")
  @ColumnInfo(name = "lastname")
  val lastname: String = "Unknown",

  @SerialName("surname")
  @ColumnInfo(name = "surname")
  val surname: String = "Unknown",

  val born: String = "Unknown",

  val sex: String = "Unknown",

  var phone: String = "Unknown",

  val subsidia: Byte = 0,

  val vkl: Byte = 0,

  val inn: String = "Unknown",

  val document: String = "Unknown",

  val seria: String = "Unknown",

  val nomer: String = "Unknown",

  val datav: String? = "Unknown",

  val organ: String = "Unknown"
)
