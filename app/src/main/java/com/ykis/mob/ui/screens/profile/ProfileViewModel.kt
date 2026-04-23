package com.ykis.mob.ui.screens.profile

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ykis.mob.core.Resource
import com.ykis.mob.domain.ClearDatabase
import com.ykis.mob.firebase.service.repo.FirebaseService
import com.ykis.mob.firebase.service.repo.LogService
import com.ykis.mob.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
class ProfileViewModel(
  private val clearDatabase: ClearDatabase,
  private val firebaseService: FirebaseService,
  logService: LogService
) : BaseViewModel(logService) {

  // Стейты ответов
  private val _deleteAccountResponse = MutableStateFlow<Resource<Boolean>>(Resource.Success(false))
  val deleteAccountResponse = _deleteAccountResponse.asStateFlow()

  // Данные профиля (уже были у тебя)
  val uid get() = firebaseService.uid
  val displayName get() = firebaseService.displayName
  val photoUrl get() = firebaseService.photoUrl
  val email get() = firebaseService.email
  val providerId get() = firebaseService.getProvider(viewModelScope)

  // Метод удаления для UI
  fun deleteAccount(onSuccess: () -> Unit) {
    val methodName = "ProfileViewModel.deleteAccount()"

    launchCatching {
      _deleteAccountResponse.value = Resource.Loading()
      Log.d("YkisLog", "$methodName: [START] Запуск полной очистки")

      // 1. Вызываем тяжелую логику в сервисе
      firebaseService.deleteAccount()

      // 2. Чистим локальную БД Room
      Log.d("YkisLog", "$methodName: [LOCAL] Очистка Room Database")
      clearDatabase()

      _deleteAccountResponse.value = Resource.Success(true)
      Log.d("YkisLog", "$methodName: [FINISH] Успех, переход на Auth")
      onSuccess()
    }
  }

  fun signOut(onSuccess: () -> Unit) {
    launchCatching {
      firebaseService.signOut()
      clearDatabase()
      onSuccess()
    }
  }
}

