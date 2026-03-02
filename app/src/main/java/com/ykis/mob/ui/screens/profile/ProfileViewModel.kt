package com.ykis.mob.ui.screens.profile

import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.ClearDatabase
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.firebase.service.repo.RevokeAccessResponse
import com.ykis.mob.firebase.service.repo.SignOutResponse
import com.ykis.mob.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class ProfileViewModel (
    private val clearDatabase: ClearDatabase,
    private val firebaseService: FirebaseService,
    private val logService: LogService
) : BaseViewModel(logService) {

    val uid get() = firebaseService.uid
    val displayName get() = firebaseService.displayName
    val photoUrl get() = firebaseService.photoUrl
    val email get() = firebaseService.email
    val providerId get() = firebaseService.getProvider(viewModelScope)

    private val _signOutResponse = MutableStateFlow<SignOutResponse>(Resource.Success(false))
    val signOutResponse = _signOutResponse.asStateFlow()

    private val _revokeAccessResponse = MutableStateFlow<RevokeAccessResponse>(Resource.Success(false))
    val revokeAccessResponse = _revokeAccessResponse.asStateFlow()

}
