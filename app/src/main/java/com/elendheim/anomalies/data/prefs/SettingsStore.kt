package com.elendheim.anomalies.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the settings screen owns, read as one object so a screen makes one call. */
data class AppSettings(
    val highContrast: Boolean = false,
    val fontScale: Float = 1f,
    val reduceMotion: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val showDistances: Boolean = true,
    val largeTouchTargets: Boolean = false,
    val quickSpins: Boolean = false,
    val quickCatches: Boolean = false,
    val detailedMap: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "elendheim_settings")

/** Reads and writes the accessibility and comfort settings. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val highContrast = booleanPreferencesKey("high_contrast")
        val fontScale = floatPreferencesKey("font_scale")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val haptics = booleanPreferencesKey("haptics")
        val showDistances = booleanPreferencesKey("show_distances")
        val largeTouchTargets = booleanPreferencesKey("large_touch_targets")
        val quickSpins = booleanPreferencesKey("quick_spins")
        val quickCatches = booleanPreferencesKey("quick_catches")
        val detailedMap = booleanPreferencesKey("detailed_map")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            highContrast = prefs[Keys.highContrast] ?: false,
            fontScale = prefs[Keys.fontScale] ?: 1f,
            reduceMotion = prefs[Keys.reduceMotion] ?: false,
            hapticsEnabled = prefs[Keys.haptics] ?: true,
            showDistances = prefs[Keys.showDistances] ?: true,
            largeTouchTargets = prefs[Keys.largeTouchTargets] ?: false,
            quickSpins = prefs[Keys.quickSpins] ?: false,
            quickCatches = prefs[Keys.quickCatches] ?: false,
            detailedMap = prefs[Keys.detailedMap] ?: true,
        )
    }

    suspend fun setHighContrast(value: Boolean) = put { it[Keys.highContrast] = value }
    suspend fun setFontScale(value: Float) = put { it[Keys.fontScale] = value }
    suspend fun setReduceMotion(value: Boolean) = put { it[Keys.reduceMotion] = value }
    suspend fun setHaptics(value: Boolean) = put { it[Keys.haptics] = value }
    suspend fun setShowDistances(value: Boolean) = put { it[Keys.showDistances] = value }
    suspend fun setLargeTouchTargets(value: Boolean) = put { it[Keys.largeTouchTargets] = value }
    suspend fun setQuickSpins(value: Boolean) = put { it[Keys.quickSpins] = value }
    suspend fun setQuickCatches(value: Boolean) = put { it[Keys.quickCatches] = value }
    suspend fun setDetailedMap(value: Boolean) = put { it[Keys.detailedMap] = value }

    /** One write path, which means every setter behaves identically. */
    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    companion object {
        /** The font sizes offered in settings, smallest first. */
        val FONT_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)

        fun fontScaleLabel(scale: Float): String = when {
            scale <= 0.85f -> "Small"
            scale <= 1.0f -> "Default"
            scale <= 1.15f -> "Large"
            scale <= 1.3f -> "Larger"
            else -> "Largest"
        }
    }
}
