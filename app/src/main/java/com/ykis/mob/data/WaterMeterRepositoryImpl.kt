package com.ykis.mob.data

import com.ykis.mob.data.remote.water.meter.GetWaterMeterResponse
import com.ykis.mob.data.remote.water.meter.WaterMeterRemote
import com.ykis.mob.domain.meter.water.meter.WaterMeterRepository

class WaterMeterRepositoryImpl (
    private val waterMeterRemote: WaterMeterRemote,
) : WaterMeterRepository {
    override suspend fun getWaterMeterList(addressId: Int, uid: String):GetWaterMeterResponse{
        return waterMeterRemote.getWaterMeterList(addressId,uid)
    }
}
