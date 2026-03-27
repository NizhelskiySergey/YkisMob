package com.ykis.mob.ui.screens.appartment

import com.ykis.mob.domain.apartment.request.AddApartment
import com.ykis.mob.domain.apartment.request.DeleteApartment
import com.ykis.mob.domain.apartment.request.GetApartment
import com.ykis.mob.domain.apartment.request.GetApartmentList
import com.ykis.mob.domain.apartment.request.UpdateBti
import com.ykis.mob.domain.apartment.request.VerifyAdminCode

import kotlin.Lazy
class ApartmentService(
  val getApartmentList: GetApartmentList,
  val getApartment: GetApartment,
  val addApartment: AddApartment,
  val deleteApartment: DeleteApartment,
  val updateBti: UpdateBti,
  val verifyAdminCode: VerifyAdminCode
)

