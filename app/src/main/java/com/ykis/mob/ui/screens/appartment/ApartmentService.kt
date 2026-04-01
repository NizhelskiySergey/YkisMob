package com.ykis.mob.ui.screens.appartment

import com.ykis.mob.domain.apartment.request.AddApartment
import com.ykis.mob.domain.apartment.request.DeleteApartment
import com.ykis.mob.domain.apartment.request.DeleteUserAccount
import com.ykis.mob.domain.apartment.request.GetApartment
import com.ykis.mob.domain.apartment.request.GetApartmentList
import com.ykis.mob.domain.apartment.request.SaveUserUid
import com.ykis.mob.domain.apartment.request.UpdateBti
import com.ykis.mob.domain.apartment.request.VerifyAdminCode

class ApartmentService(
  val getApartmentList: GetApartmentList,
  val getApartment: GetApartment,
  val addApartment: AddApartment,
  val verifyAdminCode: VerifyAdminCode,
  val deleteApartment: DeleteApartment,
  val updateBti: UpdateBti,
  val saveUserUid: SaveUserUid,
  val deleteUserAccount: DeleteUserAccount


)


