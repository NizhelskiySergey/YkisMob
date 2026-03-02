package com.ykis.mob.data.cache.dao


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ykis.mob.domain.meter.water.reading.WaterReadingEntity

@Dao
interface
WaterReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWaterReading(waterMeter: List<WaterReadingEntity>)

    @Query("select * from water_reading where vodomer_id = :vodomerId")
    fun getWaterReadings(vodomerId: Int): List<WaterReadingEntity>

    @Query("delete from water_reading")
    fun deleteAllWaterReadings()

    @Query("delete from water_reading where address_id not in (:addressIds)")
    fun deleteWaterReadingByApartment(addressIds: List<Int>)

    @Query("delete from water_reading where pok_id = :readingId ")
    fun deleteWaterReadingById(readingId:Int)
}
