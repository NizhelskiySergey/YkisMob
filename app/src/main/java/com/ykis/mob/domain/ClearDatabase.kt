package com.ykis.mob.domain

import android.util.Log
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

class ClearDatabase : KoinComponent {
  // База данных создастся только при вызове invoke(), а не при старте ViewModel
  private val db: AppDatabase by inject()

  operator fun invoke() = flow {
    emit(Resource.Loading())
    try {
      db.clearAllTables() // База инициализируется ТОЛЬКО ЗДЕСЬ
      emit(Resource.Success(true))
    } catch (e: Exception) {
      emit(Resource.Error(e.message))
    }
  }.flowOn(Dispatchers.IO)
}


