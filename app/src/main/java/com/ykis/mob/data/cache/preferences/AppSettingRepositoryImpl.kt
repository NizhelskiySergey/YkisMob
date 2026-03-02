package com.ykis.mob.data.cache.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: androidx.datastore.core.DataStore<Preferences> by preferencesDataStore(
    name = "APP_SETTINGS"
)
class AppSettingsRepositoryImpl (
    private val context: Context,
) : AppSettingsRepository {
    override suspend fun putThemeStrings(key: String, value: String) {
        val preferencesKey = stringPreferencesKey(key)
        context.themeDataStore.edit {
            it[preferencesKey] = value
        }
    }
  override suspend fun getThemeStrings(key: String): Flow<String?> =
    context.themeDataStore.data
      .map { preferences -> preferences[stringPreferencesKey(key)] }
      .distinctUntilChanged() // Чтобы не триггерить UI без изменений

}
