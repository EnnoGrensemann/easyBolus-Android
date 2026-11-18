package de.easybolus.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "easybolus_settings")

data class UserSettings(
    val carbRatio: Double = 10.0,           // g Kohlenhydrate pro 1 IE
    val isf: Double = 40.0,                 // mg/dl Senkung pro 1 IE
    val targetBg: Int = 100,                // Ziel-Blutzucker (mg/dl)
    val targetRangeMin: Int = 80,           // Grüner Zielbereich Min (mg/dl)
    val targetRangeMax: Int = 180,          // Grüner Zielbereich Max (mg/dl)
    val diaHours: Double = 4.0,             // Insulin-Wirkdauer in Stunden
    val penStepSize: Double = 0.5,          // 0.5 IE oder 1.0 IE
    val carbAbsorptionHours: Double = 3.0   // Dauer für lineare COB-Anzeige (h)
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CARB_RATIO = doublePreferencesKey("carb_ratio")
        val ISF = doublePreferencesKey("isf")
        val TARGET_BG = intPreferencesKey("target_bg")
        val TARGET_RANGE_MIN = intPreferencesKey("target_range_min")
        val TARGET_RANGE_MAX = intPreferencesKey("target_range_max")
        val DIA_HOURS = doublePreferencesKey("dia_hours")
        val PEN_STEP_SIZE = doublePreferencesKey("pen_step_size")
        val CARB_ABSORPTION_HOURS = doublePreferencesKey("carb_absorption_hours")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            carbRatio = prefs[Keys.CARB_RATIO] ?: 10.0,
            isf = prefs[Keys.ISF] ?: 40.0,
            targetBg = prefs[Keys.TARGET_BG] ?: 100,
            targetRangeMin = prefs[Keys.TARGET_RANGE_MIN] ?: 80,
            targetRangeMax = prefs[Keys.TARGET_RANGE_MAX] ?: 180,
            diaHours = prefs[Keys.DIA_HOURS] ?: 4.0,
            penStepSize = prefs[Keys.PEN_STEP_SIZE] ?: 0.5,
            carbAbsorptionHours = prefs[Keys.CARB_ABSORPTION_HOURS] ?: 3.0
        )
    }

    suspend fun updateSettings(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CARB_RATIO] = settings.carbRatio.coerceAtLeast(0.5)
            prefs[Keys.ISF] = settings.isf.coerceAtLeast(1.0)
            prefs[Keys.TARGET_BG] = settings.targetBg.coerceIn(60, 200)
            prefs[Keys.TARGET_RANGE_MIN] = settings.targetRangeMin.coerceIn(50, 140)
            prefs[Keys.TARGET_RANGE_MAX] = settings.targetRangeMax.coerceIn(120, 300)
            prefs[Keys.DIA_HOURS] = settings.diaHours.coerceIn(2.0, 8.0)
            prefs[Keys.PEN_STEP_SIZE] = if (settings.penStepSize >= 1.0) 1.0 else 0.5
            prefs[Keys.CARB_ABSORPTION_HOURS] = settings.carbAbsorptionHours.coerceIn(1.0, 6.0)
        }
    }
}
