package com.ykis.mob.ui.screens.family

import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.family.request.FamilyParams
import com.ykis.mob.domain.family.request.GetFamilyList
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class FamilyListViewModel (
    private val getFamilyList: GetFamilyList,
    private val logService: LogService,
) : BaseViewModel(logService) {

    private val _state = MutableStateFlow(FamilyState())
    val state : StateFlow<FamilyState> = _state.asStateFlow()

    fun getFamilyList(uid:String, addressId:Int) {
        this.getFamilyList(FamilyParams(uid , addressId)).onEach {
                result->
            when(result){
                is Resource.Success -> {
                    this._state.value = FamilyState(familyList = result.data ?: emptyList() , isLoading = false)
                }
                is Resource.Error -> {
                    this._state.value = FamilyState(error = result.message ?: "Unexpected error!")
                }
                is Resource.Loading -> {
                    this._state.value = FamilyState(isLoading = true)
                }
            }
        }.launchIn(this.viewModelScope)
    }
}
