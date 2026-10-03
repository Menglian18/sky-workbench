package com.nana.workbench.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 界面偏好的存储：当前主题、字体档位、是否跟随系统深色。 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val themeId = stringPreferencesKey("theme_id")
        val fontScaleId = stringPreferencesKey("font_scale_id")
        val followSystemDark = booleanPreferencesKey("follow_system_dark")
    }

    val themeId: Flow<String?> = context.settingsDataStore.data.map { it[Keys.themeId] }
    val fontScaleId: Flow<String?> = context.settingsDataStore.data.map { it[Keys.fontScaleId] }
    val followSystemDark: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.followSystemDark] ?: false }

    suspend fun setThemeId(id: String) {
        context.settingsDataStore.edit { it[Keys.themeId] = id }
    }

    suspend fun setFontScaleId(id: String) {
        context.settingsDataStore.edit { it[Keys.fontScaleId] = id }
    }

    suspend fun setFollowSystemDark(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.followSystemDark] = enabled }
    }
}
