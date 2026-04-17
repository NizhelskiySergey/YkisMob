package com.ykis.mob.ui.screens.service.list

import com.ykis.mob.domain.service.ServiceEntity
import com.ykis.mob.ui.navigation.ContentDetail

data class TotalDebtState(
    val showDetail : Boolean = false,
    val serviceDetail: ContentDetail = ContentDetail.OSBB,
    val totalDebt : ServiceEntity = ServiceEntity(),
    val isLoading: Boolean = true,
    val error: String = "" // Добавь эту строку
)
