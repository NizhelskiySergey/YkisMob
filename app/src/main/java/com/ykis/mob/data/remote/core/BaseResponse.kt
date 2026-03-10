package com.ykis.mob.data.remote.core

// Интерфейс не несет в себе полей для сериализации, только контракт
interface BaseResponse {
  val success: Int
  val message: String
}
