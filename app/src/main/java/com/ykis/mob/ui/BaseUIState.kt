package com.ykis.mob.ui

import com.ykis.mob.domain.UserRole
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.ui.navigation.ContentDetail

data class BaseUIState(
    val uid: String? = null,
    val displayName: String? = null,

    val email: String? = null,
    val photoUrl: String? = null,
    var apartment: ApartmentEntity = ApartmentEntity(),
    val selectedContentDetail: ContentDetail = ContentDetail.BTI,
    val apartments: List<ApartmentEntity> = emptyList(),
    val osmdId: Int = 0,
    val houseId: Int = 0,
    val addressId: Int = 0,
    val address: String = "",
    val osbb: String = "",
    val addressNumber: String? = null,
    val isDetailOnlyOpen: Boolean = false,
    val isLoading: Boolean = false,
    val mainLoading: Boolean = true,
    val isGlobalLoading: Boolean = false,
    val apartmentLoading : Boolean = true,
    val error: String? = null,
    val showDetail : Boolean = false,
    val userRole : UserRole = UserRole.StandardUser,
    val osbbRoleId : Int? =  null
)
