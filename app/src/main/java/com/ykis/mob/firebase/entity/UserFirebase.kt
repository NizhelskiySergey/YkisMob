/*
Copyright 2022 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.ykis.mob.firebase.entity

import com.ykis.mob.domain.UserRole
import com.ykis.mob.ui.screens.chat.UserEntity
/**
 * Модель данных пользователя, полученная из Firebase Auth и Firestore.
 */
data class UserFirebase(
  val uid: String = "",
  val email: String = "",
  val isEmailVerification: Boolean = false,
  val provider: String? = null,
  val name: String? = null,        // Сюда приходит "Адрес | Фамилия" или Nickname
  val phone: String? = null,
  val photoUrl: String? = null,    // Добавили поле для фото

  // --- Поля для управления доступом ---
  val userRole: String = "StandardUser", // В Firestore обычно хранится как String

  // ID организации (ОСББ)
  val osbbId: Int = 0,

  // ID конкретной квартиры
  val addressId: Int = 0,

  // СПИСОК ТОКЕНОВ ДЛЯ ПУШЕЙ (Критично для работы нескольких админов)
  val fcmTokens: List<String>? = emptyList()
)

/**
 * Маппер из Firebase-модели в Entity-модель для UI
 */
/**
 * Маппер из Firebase-модели в Entity-модель для UI
 */
fun UserFirebase.toEntity(): UserEntity {
  return UserEntity(
    uid = this.uid,
    // ИСПРАВЛЕНО: Убрали обращение к несуществующему displayName в UserFirebase
    // Используем name, а если он null — email
    displayName = this.name ?: this.email,
    photoUrl = this.photoUrl,
    // Преобразуем строку "OsbbUser" в Enum UserRole.OsbbUser
    userRole = UserRole.entries.find { it.name == this.userRole } ?: UserRole.StandardUser,
    email = this.email,
    address = this.name ?: "",
    osbbId = this.osbbId,
    addressId = this.addressId,
    // Мапим fcmTokens из БД в tokens в Entity
    tokens = this.fcmTokens ?: emptyList()
  )
}





