package com.ykis.mob.data.cache.apartment

import com.ykis.mob.data.cache.dao.ApartmentDao
import com.ykis.mob.domain.apartment.ApartmentEntity

class ApartmentCacheImpl (
    private val apartmentDao: ApartmentDao
): ApartmentCache {


    override suspend fun insertApartmentList(apartment: List<ApartmentEntity>) {
        apartmentDao.insertApartmentList(apartment)
    }

    override suspend fun getApartmentsByUser(): List<ApartmentEntity> {
        return apartmentDao.getApartmentList()
    }


    override suspend fun deleteAllApartments() {
        apartmentDao.deleteAllApartments()
    }

    override suspend fun deleteFlat(addressId: Int) {
        apartmentDao.deleteFlat(addressId)
    }

    override suspend fun getApartmentById(addressId: Int): ApartmentEntity? {
        return apartmentDao.getFlatById(addressId)
    }


}
