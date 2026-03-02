package com.ykis.mob.data.cache.heat.reading

import com.ykis.mob.data.cache.dao.HeatReadingDao
import com.ykis.mob.domain.meter.heat.reading.HeatReadingEntity

class HeatReadingCacheImpl (
    private val heatReadingDao: HeatReadingDao
) : HeatReadingCache {
    override fun insertHeatReading(heatReading: List<HeatReadingEntity>) {
        heatReadingDao.insertHeatReading(heatReading)
    }

    override fun getHeatReading(teplomerId: Int): List<HeatReadingEntity> {
        return heatReadingDao.getHeatReading(teplomerId)
    }

    override fun deleteAllHeatReading() {
        heatReadingDao.deleteAllHeatReadings()
    }

    override fun deleteHeatReadingsByApartment(addressIds: List<Int>) {
        heatReadingDao.deleteHeatReadingsByApartment(addressIds)
    }


}
