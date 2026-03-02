package com.ykis.mob.domain.meter.heat.reading.request
import com.ykis.mob.R
import com.ykis.mob.core.Resource
import com.ykis.mob.core.snackbar.SnackbarManager
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.data.remote.core.BaseResponse
import com.ykis.mob.domain.meter.heat.reading.HeatReadingRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВИТЬ
import retrofit2.HttpException
import java.io.IOException

class DeleteLastHeatReading (
  private val repository: HeatReadingRepository,
  private val database: AppDatabase
) {
  // Явно указываем тип в flow<...>, чтобы компилятор не ругался на BaseResponse?
  operator fun invoke(readingId : Int, uid: String): Flow<Resource<BaseResponse?>> = flow<Resource<BaseResponse?>> {
    try {
      emit(Resource.Loading())

      val response = repository.deleteLastHeatReading(readingId, uid)

      if (response.success == 1) {
        // Удаление из локальной базы (теперь безопасно на IO)
        database.heatReadingDao().deleteHeatReadingById(readingId)
        emit(Resource.Success(response))
      } else {
        emit(Resource.Error("Ошибка удаления на сервере"))
      }
    } catch (e: HttpException) {
      SnackbarManager.showMessage(e.message() ?: "HTTP Error")
      emit(Resource.Error())
    } catch (e: IOException) {
      SnackbarManager.showMessage(R.string.error_network)
      emit(Resource.Error())
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unknown error"))
    }
  }.flowOn(Dispatchers.IO) // <--- РЕШЕНИЕ CRASH ROOM
}
