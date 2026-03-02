package com.ykis.mob.data.cache.family

import com.ykis.mob.data.cache.dao.FamilyDao
import com.ykis.mob.domain.family.FamilyEntity

class FamilyCacheImpl (
    private val familyDao: FamilyDao
) : FamilyCache {
    override fun addFamilyByUser(family: List<FamilyEntity>) {
        familyDao.insertFamily(family)
    }

    override fun getFamilyByApartment(addressId: Int): List<FamilyEntity> {
        return familyDao.getFamilyByApartment(addressId)
    }

    override fun deleteAllFamily() {
        familyDao.deleteAllFamily()
    }

    override fun deleteFamilyByApartment(addressIds: List<Int>) {
        familyDao.deleteFamilyByApartment(addressIds)
    }


}
