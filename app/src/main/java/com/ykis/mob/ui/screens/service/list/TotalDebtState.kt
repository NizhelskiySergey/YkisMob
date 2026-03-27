package com.ykis.mob.ui.screens.service.list

import androidx.compose.ui.graphics.vector.ImageVector
import com.ykis.mob.domain.service.ServiceEntity
import com.ykis.mob.ui.navigation.ContentDetail

data class TotalDebtState(
    val showDetail : Boolean = false,
    val serviceDetail: ContentDetail = ContentDetail.OSBB,
    val totalDebt : ServiceEntity = ServiceEntity(),
    val isLoading: Boolean = true
)
