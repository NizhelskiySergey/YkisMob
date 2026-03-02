package com.ykis.mob.data

import com.ykis.mob.data.remote.service.GetServiceResponse
import com.ykis.mob.data.remote.service.ServiceRemote
import com.ykis.mob.domain.service.ServiceRepository
import com.ykis.mob.domain.service.request.ServiceParams
class ServiceRepositoryImpl (
    private val serviceRemote : ServiceRemote
):ServiceRepository{
    override suspend fun getFlatDetailService(params: ServiceParams):GetServiceResponse{
        return serviceRemote.getFlatDetailServices(params)
    }
    override suspend fun getTotalDebtService(params: ServiceParams): GetServiceResponse {
        return serviceRemote.getTotalDebtService(params)
    }
}
