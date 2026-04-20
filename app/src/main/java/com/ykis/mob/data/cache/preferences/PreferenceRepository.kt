package com.ykis.mob.data.cache.preferences

import android.content.Context

interface PreferenceRepository {
  suspend fun isUserAgreed(): Boolean
  suspend fun setAgreement(agreed: Boolean)

}

