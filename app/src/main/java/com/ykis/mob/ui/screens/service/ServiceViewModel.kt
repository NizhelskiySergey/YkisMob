package com.ykis.mob.ui.screens.service

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.payment.request.GetPaymentList
import com.ykis.mob.domain.payment.request.InsertPayment
import com.ykis.mob.domain.payment.request.InsertPaymentParams
import com.ykis.mob.domain.service.request.GetFlatServices
import com.ykis.mob.domain.service.request.GetTotalDebtServices
import com.ykis.mob.domain.service.request.ServiceParams
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import com.ykis.mob.ui.navigation.ContentDetail
import com.ykis.mob.ui.screens.service.detail.ServiceState
import com.ykis.mob.ui.screens.service.list.TotalDebtState
import com.ykis.mob.ui.screens.service.payment.list.PaymentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ServiceViewModel (
    private val getFlatService: GetFlatServices,
    private val getTotalDebtServices: GetTotalDebtServices,
    private val getPaymentListRepo : GetPaymentList,
    private val insertPaymentRepo : InsertPayment,
    private val logService: LogService
) : BaseViewModel(logService) {

    private val _detailState = MutableStateFlow(ServiceState())
    val detailState: StateFlow<ServiceState> = _detailState.asStateFlow()

    private val _totalDebtState = MutableStateFlow(TotalDebtState())
    val totalDebtState = _totalDebtState.asStateFlow()

    private val _paymentState = MutableStateFlow(PaymentState())
    val paymentState = _paymentState.asStateFlow()

    private val _insertPaymentLoading = MutableStateFlow(false)
    val insertPaymentLoading = _insertPaymentLoading.asStateFlow()

    fun setContentDetail (contentDetail: ContentDetail){
        _totalDebtState.value = _totalDebtState.value.copy(
            serviceDetail = contentDetail,
            showDetail = true
        )
    }
    fun closeContentDetail(){
        _totalDebtState.value = _totalDebtState.value.copy(
            showDetail = false
        )
    }

    fun getTotalServiceDebt(params: ServiceParams){
        this.getTotalDebtServices(
            params = params
        ).onEach {
            result ->
            when(result){
                is Resource.Success -> {
                    this._totalDebtState.value = this._totalDebtState.value.copy(
                       totalDebt = result.data!! , isLoading = false
                    )
                }
                is Resource.Error -> {

                }
                is Resource.Loading -> {
                    this._totalDebtState.value = this._totalDebtState.value.copy(isLoading = true)
                }
            }
        }.launchIn(this.viewModelScope)
    }
     fun getDetailService(params: ServiceParams){
        this.getFlatService(
            params = params
        ).onEach {
            result->
            when(result){
                is Resource.Success -> {
                    this._detailState.value = detailState.value.copy(services = result.data ?: emptyList(), isLoading = false)
                }
                is Resource.Error -> {
                    this._detailState.value = detailState.value.copy(error = result.message ?: "Unexpected error!")
                }
                is Resource.Loading -> {
                    this._detailState.value = detailState.value.copy(isLoading = true)
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun getPaymentList(
        addressId : Int,
        year:String,
        uid:String
    ) {
        this.getPaymentListRepo(
            addressId, year, uid
        ).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    this._paymentState.value = paymentState.value.copy(
                        paymentList = result.data ?: emptyList(),
                        isLoading = false
                    )
                }

                is Resource.Error -> {
                    this._paymentState.value = paymentState.value.copy(
//                        isLoading = false
                    )
                }

                is Resource.Loading -> {
                    this._paymentState.value = paymentState.value.copy(
                        isLoading = true
                    )
                }
            }
        }.launchIn(this.viewModelScope)
    }

    fun insertPayment(
        params:InsertPaymentParams,
        onSuccess : (String) -> Unit
    ){
        this.insertPaymentRepo(
            params
        ).onEach { result ->
            when (result) {
                is Resource.Success -> {
                    _insertPaymentLoading.value = false
                    //if open in WebView
                    onSuccess(result.data.toString().replace("/" , "*"))
                    //if open in Browser
//                    onSuccess(result.data.toString())
                    Log.d("link_test" , result.data.toString())
                }

                is Resource.Error -> {
                    _insertPaymentLoading.value = false
                    Log.d("link_test" , "error")
                }

                is Resource.Loading -> {
                    _insertPaymentLoading.value = true
                }
            }
        }.launchIn(this.viewModelScope)
    }
}
