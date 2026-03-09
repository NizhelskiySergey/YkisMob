/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.ykis.mob.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.snackbar.SnackbarManager.showMessage
import com.ykis.mob.core.snackbar.SnackbarMessage.Companion.toSnackbarMessage
import com.ykis.mob.firebase.service.repo.LogService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


open class BaseViewModel(
    private val logService: LogService,
) : ViewModel() {

    val _uiState = MutableStateFlow(BaseUIState(

    ))
    val uiState: StateFlow<BaseUIState> = _uiState.asStateFlow()
//
//  // Включить лоадер
//  fun showProgress() {
//    _uiState.update { it.copy(isGlobalLoading = true) }
//  }
//
//  // Выключить лоадер
//  fun hideProgress() {
//    _uiState.update { it.copy(isGlobalLoading = false) }
//  }


    fun launchCatching(snackbar: Boolean = true, block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(
            CoroutineExceptionHandler { _, throwable ->
                if (snackbar) {
                    showMessage(throwable.toSnackbarMessage())
                }
                logService.logNonFatalCrash(throwable)
            },
            block = block
        )

    fun navigateBack(popUpScreen: () -> Unit) {
        launchCatching {
            popUpScreen()
        }
    }


}


