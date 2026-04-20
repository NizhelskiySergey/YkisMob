package com.ykis.mob.data.cache.apartment

import com.ykis.mob.domain.apartment.ApartmentEntity


interface ApartmentCache {
    suspend fun insertApartmentList(apartment:List<ApartmentEntity>)
    suspend fun getApartmentsByUser():List<ApartmentEntity>

    suspend fun deleteAllApartments()
    suspend fun deleteFlat(addressId: Int)
    suspend fun getApartmentById(addressId: Int): ApartmentEntity?
}
