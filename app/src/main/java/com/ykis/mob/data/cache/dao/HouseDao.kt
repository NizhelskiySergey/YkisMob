package com.ykis.mob.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.HouseEntity
import com.ykis.mob.domain.apartment.RaionEntity
@Dao
interface HouseDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHouseList(houses: List<HouseEntity>)

  @Query("SELECT * FROM houses WHERE raionId = :raionId")
  suspend fun getHousesByRaion(raionId: Int): List<HouseEntity>

  @Query("SELECT * FROM houses")
  suspend fun getHouseList(): List<HouseEntity>
}
