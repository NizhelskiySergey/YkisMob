package com.ykis.mob.ui.screens.appartment

import com.ykis.mob.domain.apartment.request.AddApartment
import com.ykis.mob.domain.apartment.request.DeleteApartment
import com.ykis.mob.domain.apartment.request.DeleteUserAccount
import com.ykis.mob.domain.apartment.request.GetApartment
import com.ykis.mob.domain.apartment.request.GetApartmentList
import com.ykis.mob.domain.apartment.request.GetHouseList
import com.ykis.mob.domain.apartment.request.GetOsbbApartmentsList
import com.ykis.mob.domain.apartment.request.GetRaionList
import com.ykis.mob.domain.apartment.request.SaveUserUid
import com.ykis.mob.domain.apartment.request.UpdateBti
import com.ykis.mob.domain.apartment.request.VerifyAdminCode

class ApartmentService(
  val getApartmentList: GetApartmentList,     // Для жильца (с БД)
  val getOsbbApartmentsList: GetOsbbApartmentsList, // Для админа (чистая сеть)
  val getRaionList: GetRaionList, // Для админа (чистая сеть)
  val getHouseList: GetHouseList, // Для админа (чистая сеть)
  val getApartment: GetApartment,
  val addApartment: AddApartment,
  val verifyAdminCode: VerifyAdminCode,
  val deleteApartment: DeleteApartment,
  val updateBti: UpdateBti,
  val saveUserUid: SaveUserUid,
  val deleteUserAccount: DeleteUserAccount
)



