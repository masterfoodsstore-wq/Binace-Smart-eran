package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "binace_smart_earn_prefs")

class ThemePreferences(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme_enabled")
    private val pendingReferralCodeKey = stringPreferencesKey("pending_referral_code")

    val isDarkTheme: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[darkThemeKey]
    }

    val pendingReferralCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[pendingReferralCodeKey].orEmpty()
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[darkThemeKey] = enabled
        }
    }

    suspend fun setPendingReferralCode(code: String) {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[pendingReferralCodeKey] = clean
        }
    }

    suspend fun clearPendingReferralCode() {
        context.dataStore.edit { prefs ->
            prefs.remove(pendingReferralCodeKey)
        }
    }
}
