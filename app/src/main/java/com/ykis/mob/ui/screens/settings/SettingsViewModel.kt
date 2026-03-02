package com.ykis.mob.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import com.ykis.mob.MainApplication
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.preferences.AppSettingsRepository
import com.ykis.mob.domain.ClearDatabase
import com.ykis.mob.firebase.service.repo.ConfigurationService
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DecimalFormatSymbols
import java.util.Locale

class NewSettingsViewModel (
    private val dataStore: AppSettingsRepository,
    private val application: MainApplication,
    private val clearDatabase: ClearDatabase,
    private val firebaseService: FirebaseService,
) : ViewModel() {

  val displayName get() = firebaseService.displayName
  val photoUrl get() = firebaseService.photoUrl
  val email get() = firebaseService.email

  private val _theme = MutableStateFlow<String?>(null)
  val theme = _theme.asStateFlow()

  private val _loading = MutableStateFlow(false)
  val loading = _loading.asStateFlow()

  private val providerId get() = firebaseService.getProvider(viewModelScope)
  init {
    getThemeValue()
    viewModelScope.launch(Dispatchers.IO) { // Явно уходим с Main
      Firebase.messaging.subscribeToTopic("chat").await()
    }
  }



  fun signOut(onSuccess: () -> Unit) {
    firebaseService.signOut().onEach { result ->
      when (result) {
        is Resource.Success -> {
          _loading.value = false
          onSuccess()
        }

        is Resource.Error -> {
          _loading.value = false
          SnackbarManager.showMessage(result.resourceMessage)
        }

        is Resource.Loading -> {
          _loading.value = true
        }
      }
    }.launchIn(viewModelScope)
    this.clearDatabase().launchIn(this.viewModelScope)
  }

  fun revokeAccess(onSuccess: () -> Unit) {
    if (providerId == "password") {
      firebaseService.revokeAccessEmail().onEach { result ->
        when (result) {
          is Resource.Success -> {
            _loading.value = false
            onSuccess()
          }

          is Resource.Error -> {
            _loading.value = false
            SnackbarManager.showMessage(result.resourceMessage)
          }

          is Resource.Loading -> {
            _loading.value = true
          }
        }
      }.launchIn(viewModelScope)
    } else if (providerId == "google.com") {
      firebaseService.revokeAccess().onEach { result ->
        when (result) {
          is Resource.Success -> {
            _loading.value = false
            onSuccess()
          }

          is Resource.Error -> {
            _loading.value = false
            SnackbarManager.showMessage(result.resourceMessage)
          }

          is Resource.Loading -> {
            _loading.value = true
          }
        }
      }.launchIn(viewModelScope)
    }
  }

  fun setThemeValue(value: String) {
    viewModelScope.launch {
      dataStore.putThemeStrings(key = "theme", value = value)
      // getThemeValue() здесь больше НЕ нужен
    }
  }

  fun getThemeValue() {
    viewModelScope.launch {
      dataStore.getThemeStrings(key = "theme").collect { value ->
        _theme.value = value
        // Синхронизируем с полем в Application, если это необходимо для легаси-кода
        application.theme.value = value ?: "system"
      }
    }
  }
}
