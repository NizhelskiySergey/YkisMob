package com.ykis.mob.domain.apartment.request

import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

class GetApartment (
    private val repository: ApartmentRepository,
    private val database: AppDatabase
){
  operator fun invoke(addressId: Int, uid: String): Flow<Resource<ApartmentEntity>> = flow {
    try {
      emit(Resource.Loading())

      // Теперь эти вызовы будут на Dispatchers.IO
      val apartment = database.apartmentDao().getFlatById(addressId = addressId)
      if (apartment != null) {
        emit(Resource.Success(apartment))
      }

      val response = repository.getApartment(addressId, uid)
      if (response.success == 1) {
        emit(Resource.Success(response.apartment))
        database.apartmentDao().insertApartmentList(listOf(response.apartment))
      } else throw ExceptionWithResourceMessage(R.string.generic_error)

    } catch (e: ExceptionWithResourceMessage) {
      SnackbarManager.showMessage(e.resourceMessage)
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error!"))
    } catch (e: IOException) {
      val apartment = database.apartmentDao().getFlatById(addressId = addressId)
      if (apartment != ApartmentEntity()) {
        emit(Resource.Success(apartment))
      }
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    }
  }.flowOn(Dispatchers.IO)
}
