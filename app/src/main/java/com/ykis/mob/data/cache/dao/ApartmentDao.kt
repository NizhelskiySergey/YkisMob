package com.ykis.mob.data.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ykis.mob.domain.apartment.ApartmentEntity

@Dao
interface ApartmentDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertApartmentList(apartment: List<ApartmentEntity>)

  @Query("DELETE FROM apartment")
  suspend fun deleteAllApartments()

  @Query("DELETE FROM apartment WHERE address_id = :addressId")
  suspend fun deleteFlat(addressId: Int)

  @Query("SELECT * FROM apartment WHERE address_id = :addressId")
  suspend fun getFlatById(addressId: Int): ApartmentEntity?

  // КРИТИЧНО: Получаем список только для конкретного пользователя
  @Query("SELECT * FROM apartment WHERE uid = :uid")
  suspend fun getApartmentListByUid(uid: String): List<ApartmentEntity>

  // Оставляем общий метод, если он нужен для отладки
  @Query("SELECT * FROM apartment")
  suspend fun getApartmentList(): List<ApartmentEntity>
}
