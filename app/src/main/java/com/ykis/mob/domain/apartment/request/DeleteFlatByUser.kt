package com.ykis.mob.domain.apartment.request
import com.ykis.mob.R
import com.ykis.mob.core.ExceptionWithResourceMessage
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.apartment.ApartmentRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import java.io.IOException

class DeleteApartment (
  private val repository: ApartmentRepository,
  private val appDatabase: AppDatabase
){
  operator fun invoke (addressId:Int, uid:String) : Flow<Resource<BaseResponse>> = flow {
    try {
      emit(Resource.Loading())
      val response = repository.deleteApartment(addressId, uid)

      if (response.success == 1) {
        // Теперь удаление из локальной БД безопасно на фоновом потоке
        appDatabase.apartmentDao().deleteFlat(addressId)
        emit(Resource.Success(response))
      } else {
        throw ExceptionWithResourceMessage(R.string.error_delete_flat)
      }
    } catch (e: ExceptionWithResourceMessage) {
      emit(Resource.Error(resourceMessage = e.resourceMessage, message = null))
    } catch (e: IOException) {
      emit(Resource.Error(resourceMessage = R.string.error_network_delete))
    } catch (ex: Exception) {
      emit(Resource.Error(message = ex.message))
    }
  }.flowOn(Dispatchers.IO) // <--- РЕШЕНИЕ CRASH ROOM
}

