package com.ykis.mob.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.core.snackbar.SnackbarMessage.Companion.toSnackbarMessage
import com.ykis.mob.firebase.service.repo.LogService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Базовая модель для всех экранов YkisPam.
 * Использует [LogService] для отслеживания ошибок Firebase Crashlytics.
 */
open class BaseViewModel(
  private val logService: LogService,
) : ViewModel() {

  // Глобальное состояние (UID, Роль, Загрузка), общее для всех
  protected val _uiState = MutableStateFlow(BaseUIState())
  val uiState: StateFlow<BaseUIState> = _uiState.asStateFlow()

  /**
   * Безопасный запуск корутин с автоматическим логированием ошибок в Firebase.
   * @param snackbar показывать ли сообщение об ошибке пользователю.
   * @param showLoader показывать ли индикатор загрузки во время выполнения.
   */
  fun launchCatching(
    snackbar: Boolean = true,
    showLoader: Boolean = false,
    block: suspend CoroutineScope.() -> Unit
  ) = viewModelScope.launch(
    CoroutineExceptionHandler { _, throwable ->
      if (showLoader) hideProgress()
      if (snackbar) {
        SnackbarManager.showMessage(throwable.toSnackbarMessage())
      }
      logService.logNonFatalCrash(throwable)
    }
  ) {
    if (showLoader) showProgress()
    block()
    if (showLoader) hideProgress()
  }

  // Управление состоянием загрузки (расскомментировано и оптимизировано)
  fun showProgress() {
    _uiState.update { it.copy(isLoading = true) }
  }

  fun hideProgress() {
    _uiState.update { it.copy(isLoading = false) }
  }

  /**
   * Хелпер для вывода быстрых сообщений (например, из PHP бэкенда)
   */
  fun showMessage(message: Int) {
    SnackbarManager.showMessage(message)
  }
}
