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


  suspend fun insertHouseList(raion: List<HouseEntity>)

  @Query("SELECT * FROM houses WHERE houseId = :houseId")
  suspend fun getHouseId(houseId: Int): HouseEntity?


  // Оставляем общий метод, если он нужен для отладки
  @Query("SELECT * FROM houses")
  suspend fun getHouseList(): List<HouseEntity>
}
