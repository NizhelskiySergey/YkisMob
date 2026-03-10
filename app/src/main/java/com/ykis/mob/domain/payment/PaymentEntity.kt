package com.ykis.mob.domain.payment

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable // Заменили Moshi на Kotlin Serialization
@Entity(tableName = "payment")
data class PaymentEntity(
  @PrimaryKey(autoGenerate = false)
  @SerialName("rec_id") // Заменили @Json
  @ColumnInfo(name = "rec_id")
  val recID: Int = 0,

  @SerialName("address_id")
  @ColumnInfo(name = "address_id")
  val addressID: Int = 0,

  val data: String = "Unknown",
  val kvartplata: Double = 0.00,
  val remont: Double = 0.00,
  val otoplenie: Double = 0.00,
  val voda: Double = 0.00,
  val tbo: Double = 0.00,
  val summa: Double = 0.00,
  val prixod: String = "Unknown",
  val kassa: String = "Unknown",
  val nomer: String = "Unknown",

  @SerialName("data_in")
  @ColumnInfo(name = "data_in")
  val dataIn: String = "Unknown"
)
