package com.ykis.mob.data.cache.preferences

import android.content.Context

class PreferenceRepositoryImpl(private val context: Context) : PreferenceRepository {

  private val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

  override suspend fun isUserAgreed(): Boolean = sharedPrefs.getBoolean("is_agreed", false)


  override suspend fun setAgreement(agreed: Boolean) {
    sharedPrefs.edit().putBoolean("is_agreed", agreed).apply()
  }

}
