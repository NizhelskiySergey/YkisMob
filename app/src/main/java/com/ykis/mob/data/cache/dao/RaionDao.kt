package com.ykis.mob.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.RaionEntity

@Dao
interface RaionDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)


  suspend fun insertRaionList(raion: List<RaionEntity>)

  @Query("SELECT * FROM raion WHERE raionId = :raionId")
  suspend fun getRaionId(raionId: Int): RaionEntity?


  // Оставляем общий метод, если он нужен для отладки
  @Query("SELECT * FROM raion")
  suspend fun getRaionList(): List<RaionEntity>
}
