package com.ykis.mob.ui

import com.ykis.mob.domain.UserRole
import com.ykis.mob.domain.apartment.ApartmentEntity
import com.ykis.mob.ui.navigation.ContentDetail

/**
 * Глобальное состояние приложения.
 * Содержит данные профиля, текущей квартиры и метаданные для навигации.
 */
data class BaseUIState(
  // Данные авторизации (Firebase)
  val uid: String? = null,
  val displayName: String? = null,
  val email: String? = null,
  val photoUrl: String? = null,
  val userRole: UserRole = UserRole.StandardUser, // STANDARD_USER, OSBB, WATER_SERVICE, WARM_SERVICE, GARBAGE_SERVICE


  // Данные жильца
  val apartment: ApartmentEntity = ApartmentEntity(),
  val apartments: List<ApartmentEntity> = emptyList(),
  val addressId: Int = 0,
  val address: String = "",
  val addressNumber: String? = null,

  // Данные для админов предприятий (OSBB/ОСМД)
  val osbbId: Int? = null, // ID одного из 4-х предприятий
  val osmdId: Int = 0,      // Для совместимости со старыми ключами
  val houseId: Int = 0,
  val osbb: String = "",
  // Состояние интерфейса и навигации
  val selectedContentDetail: ContentDetail = ContentDetail.BTI,
  val isDetailOnlyOpen: Boolean = false,
  val showDetail: Boolean = false,

  // Состояния загрузки (разные уровни)
  val isLoading: Boolean = false,       // Фоновая загрузка
  val mainLoading: Boolean = true,      // Первый запуск приложения
  val isGlobalLoading: Boolean = false, // Блокирующий лоадер (например, при вводе кода)
  val apartmentLoading: Boolean = true, // Загрузка данных конкретной квартиры

  val error: String? = null
)
