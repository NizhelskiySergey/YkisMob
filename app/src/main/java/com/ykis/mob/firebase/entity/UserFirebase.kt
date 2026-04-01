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

/**
 * Модель данных пользователя, полученная из Firebase Auth и Firestore.
 */
data class UserFirebase(
  val uid: String,
  val email: String,
  val isEmailVerification: Boolean = false,
  val provider: String? = null,
  val name: String? = null,
  val phone: String? = null,

  // --- Поля для управления доступом (YkisPam) ---
  // Роль из Firestore: STANDARD_USER, OSBB, WATER_SERVICE и т.д.
  val userRole: UserRole = UserRole.StandardUser,

  // ID организации (обязателен для роли OSBB, для остальных может быть 0 или null)
  val osbbId: Int? = 0
)

