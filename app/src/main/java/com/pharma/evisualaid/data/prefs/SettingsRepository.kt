package com.pharma.evisualaid.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pharma.evisualaid.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Immutable snapshot of all user-configurable settings. */
data class AppSettings(
    val companyName: String = "",
    val companyLogoUri: String? = null,
    val accentColorArgb: Long = DEFAULT_ACCENT,
    val defaultSort: SortOrder = SortOrder.DATE_NEWEST,
    /** Auto-hide delay in ms; [NEVER_HIDE] means the UI never auto-hides. */
    val autoHideDelayMs: Long = 3_000L
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF00BCD4L // medical teal
        const val NEVER_HIDE = -1L
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists [AppSettings] via Jetpack DataStore (no blocking, no SharedPreferences). */
class SettingsRepository(private val appContext: Context) {

    private object Keys {
        val COMPANY_NAME = stringPreferencesKey("company_name")
        val LOGO_URI = stringPreferencesKey("logo_uri")
        val ACCENT = longPreferencesKey("accent_argb")
        val SORT = stringPreferencesKey("default_sort")
        val AUTO_HIDE = longPreferencesKey("auto_hide_ms")
    }

    val settings: Flow<AppSettings> = appContext.dataStore.data.map { p ->
        AppSettings(
            companyName = p[Keys.COMPANY_NAME] ?: "",
            companyLogoUri = p[Keys.LOGO_URI],
            accentColorArgb = p[Keys.ACCENT] ?: AppSettings.DEFAULT_ACCENT,
            defaultSort = SortOrder.fromName(p[Keys.SORT]),
            autoHideDelayMs = p[Keys.AUTO_HIDE] ?: 3_000L
        )
    }

    suspend fun setCompanyName(name: String) =
        appContext.dataStore.edit { it[Keys.COMPANY_NAME] = name }

    suspend fun setCompanyLogoUri(uri: String?) =
        appContext.dataStore.edit {
            if (uri == null) it.remove(Keys.LOGO_URI) else it[Keys.LOGO_URI] = uri
        }

    suspend fun setAccentColor(argb: Long) =
        appContext.dataStore.edit { it[Keys.ACCENT] = argb }

    suspend fun setDefaultSort(order: SortOrder) =
        appContext.dataStore.edit { it[Keys.SORT] = order.name }

    suspend fun setAutoHideDelay(ms: Long) =
        appContext.dataStore.edit { it[Keys.AUTO_HIDE] = ms }
}
