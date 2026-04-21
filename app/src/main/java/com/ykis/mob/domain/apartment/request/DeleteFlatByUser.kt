package com.ykis.mob.domain.apartment.request
import android.util.Log
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

class DeleteApartment(
  private val repository: ApartmentRepository,
  private val appDatabase: AppDatabase
) {
  operator fun invoke(addressId: Int, uid: String): Flow<Resource<BaseResponse>> = flow {
    val methodName = "UseCase.DeleteApartment"
    try {
      emit(Resource.Loading())
      Log.d("YkisLog", "$methodName: [START] Удаление ID: $addressId")

      val response = repository.deleteApartment(addressId, uid)

      if (response.success == 1) {
        // Удаляем из Room
        appDatabase.apartmentDao().deleteFlat(addressId)
        Log.d("YkisLog", "$methodName: [DB_CLEAN] Квартира $addressId удалена из Room")
        emit(Resource.Success(response))
      } else {
        throw ExceptionWithResourceMessage(R.string.error_delete_flat)
      }
    } catch (e: ExceptionWithResourceMessage) {
      emit(Resource.Error(resourceMessage = e.resourceMessage))
    } catch (e: IOException) {
      emit(Resource.Error(resourceMessage = R.string.error_network_delete))
    } catch (ex: Exception) {
      Log.e("YkisLog", "$methodName: [FATAL] ${ex.message}")
      emit(Resource.Error(message = ex.message))
    }
  }.flowOn(Dispatchers.IO)
}


