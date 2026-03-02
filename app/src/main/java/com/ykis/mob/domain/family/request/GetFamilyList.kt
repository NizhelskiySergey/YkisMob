package com.ykis.mob.domain.family.request
import com.ykis.mob.core.Resource
import com.ykis.mob.data.cache.database.AppDatabase
import com.ykis.mob.domain.family.FamilyEntity
import com.ykis.mob.domain.family.FamilyRepository
import kotlinx.coroutines.Dispatchers // ДОБАВИТЬ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn // ДОБАВЛЕНО
import retrofit2.HttpException
import java.io.IOException

class GetFamilyList (
  private val repository: FamilyRepository,
  private val database: AppDatabase
) {
  // Убрали "?" после List<FamilyEntity>, чтобы типы совпадали
  operator fun invoke(params: FamilyParams): Flow<Resource<List<FamilyEntity>>> = flow {
    try {
      emit(Resource.Loading())

      val response = repository.getFamilyList(params)

      // Запись в базу (теперь безопасно на IO)
      database.familyDao().insertFamily(response)
      emit(Resource.Success(response))

    } catch (e: HttpException) {
      emit(Resource.Error(e.localizedMessage ?: "Unexpected error!"))
    } catch (e: IOException) {
      // Чтение из базы при отсутствии интернета
      val familyList = database.familyDao().getFamilyByApartment(
        addressId = params.addressId
      )
      if (familyList.isNotEmpty()) {
        emit(Resource.Success(familyList))
        return@flow
      }
      emit(Resource.Error("Check your internet connection"))
    } catch (e: Exception) {
      emit(Resource.Error(e.localizedMessage ?: "Unknown Error"))
    }
  }.flowOn(Dispatchers.IO) // <--- РЕШЕНИЕ CRASH
}

