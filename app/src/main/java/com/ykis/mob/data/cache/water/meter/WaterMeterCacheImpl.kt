package com.ykis.mob.data.cache.water.meter

import com.ykis.mob.data.cache.dao.WaterMeterDao
import com.ykis.mob.domain.meter.water.meter.WaterMeterEntity

class WaterMeterCacheImpl (
    private val waterMeterDao: WaterMeterDao
) : WaterMeterCache {
    override fun insertWaterMeter(waterMeters: List<WaterMeterEntity>) {
        waterMeterDao.insertWaterMeter(waterMeters)
    }

    override fun getWaterMeter(addressId: Int): List<WaterMeterEntity> {
        return waterMeterDao.getWaterMeter(addressId)
    }

    override fun deleteAllWaterMeter() {
        waterMeterDao.deleteAllWaterMeter()
    }

    override fun deleteWaterMeterByApartment(addressIds: List<Int>) {
        waterMeterDao.deleteWaterMeterByApartment(addressIds)
    }
}
