package de.easybolus.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "easybolus_settings")

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel")
}

enum class GlucoseUnit(val label: String) {
    MG_DL("mg/dl"),
    MMOL_L("mmol/L")
}

enum class CarbFactorMode {
    GRAMS_PER_IE,  // g KH pro 1 IE (z.B. 10 g/IE)
    IE_PER_10G,    // IE pro 10 g KH (z.B. 1,0 IE/10g)
    IE_PER_BE      // IE pro 1 BE = 12 g KH (z.B. 1,2 IE/BE)
}

enum class InsulinType(val label: String, val shortLabel: String, val peakMinutes: Double) {
    RAPID("Schnell (NovoRapid / Humalog)", "Schnell (75 min)", 75.0),
    ULTRA_RAPID("Ultra-Schnell (Fiasp / Lyumjev)", "Ultra-Schnell (55 min)", 55.0)
}

data class UserSettings(
    val carbRatio: Double = 10.0,           // intern immer g Kohlenhydrate pro 1 IE
    val carbFactorMode: CarbFactorMode = CarbFactorMode.GRAMS_PER_IE,
    val useHourlyCarbRatio: Boolean = false,
    val hourlyCarbRatios: List<Double> = List(24) { 10.0 },
    val isf: Double = 40.0,                 // intern immer mg/dl Senkung pro 1 IE
    val targetBg: Int = 100,                // intern immer mg/dl
    val targetRangeMin: Int = 80,           // intern immer mg/dl
    val targetRangeMax: Int = 180,          // intern immer mg/dl
    val diaHours: Double = 4.0,             // Insulin-Wirkdauer in Stunden
    val insulinType: InsulinType = InsulinType.RAPID,
    val penStepSize: Double = 0.5,          // 0.5 IE oder 1.0 IE
    val defaultBasalUnits: Double = 0.0,    // Standard-Basal-Dosis für Schnell-Eintrag
    val carbAbsorptionHours: Double = 3.0,  // Dauer für lineare COB-Anzeige (h)
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val glucoseUnit: GlucoseUnit = GlucoseUnit.MG_DL,
    val jugglucoEnabled: Boolean = true,
    val persistentNotificationEnabled: Boolean = false,
    val correctionAlertEnabled: Boolean = false,
    val correctionAlertMinUnits: Double = 1.5,
    val correctionAlertWaitMinutes: Int = 20,
    val widgetTransparencyPercent: Int = 0
) {
    fun carbRatioForHour(hour: Int): Double {
        if (!useHourlyCarbRatio || hourlyCarbRatios.size != 24) return carbRatio
        return hourlyCarbRatios[hour.coerceIn(0, 23)]
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val CARB_RATIO = doublePreferencesKey("carb_ratio")
        val CARB_FACTOR_MODE = stringPreferencesKey("carb_factor_mode")
        val USE_HOURLY_CARB_RATIO = booleanPreferencesKey("use_hourly_carb_ratio")
        val HOURLY_CARB_RATIOS = stringPreferencesKey("hourly_carb_ratios")
        val ISF = doublePreferencesKey("isf")
        val TARGET_BG = intPreferencesKey("target_bg")
        val TARGET_RANGE_MIN = intPreferencesKey("target_range_min")
        val TARGET_RANGE_MAX = intPreferencesKey("target_range_max")
        val DIA_HOURS = doublePreferencesKey("dia_hours")
        val INSULIN_TYPE = stringPreferencesKey("insulin_type")
        val PEN_STEP_SIZE = doublePreferencesKey("pen_step_size")
        val DEFAULT_BASAL_UNITS = doublePreferencesKey("default_basal_units")
        val CARB_ABSORPTION_HOURS = doublePreferencesKey("carb_absorption_hours")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GLUCOSE_UNIT = stringPreferencesKey("glucose_unit")
        val JUGGLUCO_ENABLED = booleanPreferencesKey("juggluco_enabled")
        val PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistent_notification")
        val CORRECTION_ALERT_ENABLED = booleanPreferencesKey("correction_alert_enabled")
        val CORRECTION_ALERT_MIN_UNITS = doublePreferencesKey("correction_alert_min_units")
        val CORRECTION_ALERT_WAIT_MINUTES = intPreferencesKey("correction_alert_wait_minutes")
        val WIDGET_TRANSPARENCY_PERCENT = intPreferencesKey("widget_transparency_percent")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val baseCarbRatio = prefs[Keys.CARB_RATIO] ?: 10.0
        val parsedFactorMode = prefs[Keys.CARB_FACTOR_MODE]
            ?.let { raw -> CarbFactorMode.entries.firstOrNull { it.name == raw } }
            ?: CarbFactorMode.GRAMS_PER_IE
        val parsedHourly = prefs[Keys.HOURLY_CARB_RATIOS]
            ?.split(",")
            ?.mapNotNull { it.trim().toDoubleOrNull()?.coerceAtLeast(0.5) }
            ?.takeIf { it.size == 24 }
            ?: List(24) { baseCarbRatio }
        val parsedThemeMode = prefs[Keys.THEME_MODE]
            ?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } }
            ?: ThemeMode.SYSTEM
        val parsedInsulinType = prefs[Keys.INSULIN_TYPE]
            ?.let { raw -> InsulinType.entries.firstOrNull { it.name == raw } }
            ?: InsulinType.RAPID
        val parsedUnit = prefs[Keys.GLUCOSE_UNIT]
            ?.let { raw -> GlucoseUnit.entries.firstOrNull { it.name == raw } }
            ?: GlucoseUnit.MG_DL

        UserSettings(
            carbRatio = baseCarbRatio,
            carbFactorMode = parsedFactorMode,
            useHourlyCarbRatio = prefs[Keys.USE_HOURLY_CARB_RATIO] ?: false,
            hourlyCarbRatios = parsedHourly,
            isf = prefs[Keys.ISF] ?: 40.0,
            targetBg = prefs[Keys.TARGET_BG] ?: 100,
            targetRangeMin = prefs[Keys.TARGET_RANGE_MIN] ?: 80,
            targetRangeMax = prefs[Keys.TARGET_RANGE_MAX] ?: 180,
            diaHours = prefs[Keys.DIA_HOURS] ?: 4.0,
            insulinType = parsedInsulinType,
            penStepSize = prefs[Keys.PEN_STEP_SIZE] ?: 0.5,
            defaultBasalUnits = prefs[Keys.DEFAULT_BASAL_UNITS] ?: 0.0,
            carbAbsorptionHours = prefs[Keys.CARB_ABSORPTION_HOURS] ?: 3.0,
            themeMode = parsedThemeMode,
            glucoseUnit = parsedUnit,
            jugglucoEnabled = prefs[Keys.JUGGLUCO_ENABLED] ?: true,
            persistentNotificationEnabled = prefs[Keys.PERSISTENT_NOTIFICATION] ?: false,
            correctionAlertEnabled = prefs[Keys.CORRECTION_ALERT_ENABLED] ?: false,
            correctionAlertMinUnits = prefs[Keys.CORRECTION_ALERT_MIN_UNITS] ?: 1.5,
            correctionAlertWaitMinutes = prefs[Keys.CORRECTION_ALERT_WAIT_MINUTES] ?: 20,
            widgetTransparencyPercent = (prefs[Keys.WIDGET_TRANSPARENCY_PERCENT] ?: 0).coerceIn(0, 100)
        )
    }

    suspend fun getCurrentSettings(): UserSettings = settingsFlow.first()

    suspend fun isJugglucoEnabled(): Boolean = getCurrentSettings().jugglucoEnabled

    suspend fun updateSettings(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            val sanitizedCarbRatio = settings.carbRatio.coerceAtLeast(0.5)
            val sanitizedHourly = List(24) { hour ->
                (settings.hourlyCarbRatios.getOrNull(hour) ?: sanitizedCarbRatio).coerceAtLeast(0.5)
            }
            prefs[Keys.CARB_RATIO] = sanitizedCarbRatio
            prefs[Keys.CARB_FACTOR_MODE] = settings.carbFactorMode.name
            prefs[Keys.USE_HOURLY_CARB_RATIO] = settings.useHourlyCarbRatio
            prefs[Keys.HOURLY_CARB_RATIOS] = sanitizedHourly.joinToString(",")
            prefs[Keys.ISF] = settings.isf.coerceAtLeast(1.0)
            prefs[Keys.TARGET_BG] = settings.targetBg.coerceIn(60, 200)
            prefs[Keys.TARGET_RANGE_MIN] = settings.targetRangeMin.coerceIn(50, 140)
            prefs[Keys.TARGET_RANGE_MAX] = settings.targetRangeMax.coerceIn(120, 300)
            prefs[Keys.DIA_HOURS] = settings.diaHours.coerceIn(2.0, 8.0)
            prefs[Keys.INSULIN_TYPE] = settings.insulinType.name
            prefs[Keys.PEN_STEP_SIZE] = if (settings.penStepSize >= 1.0) 1.0 else 0.5
            prefs[Keys.DEFAULT_BASAL_UNITS] = settings.defaultBasalUnits.coerceIn(0.0, 200.0)
            prefs[Keys.CARB_ABSORPTION_HOURS] = settings.carbAbsorptionHours.coerceIn(1.0, 6.0)
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.GLUCOSE_UNIT] = settings.glucoseUnit.name
            prefs[Keys.JUGGLUCO_ENABLED] = settings.jugglucoEnabled
            prefs[Keys.PERSISTENT_NOTIFICATION] = settings.persistentNotificationEnabled
            prefs[Keys.CORRECTION_ALERT_ENABLED] = settings.correctionAlertEnabled
            prefs[Keys.CORRECTION_ALERT_MIN_UNITS] = settings.correctionAlertMinUnits.coerceIn(0.5, 20.0)
            prefs[Keys.CORRECTION_ALERT_WAIT_MINUTES] = settings.correctionAlertWaitMinutes.coerceIn(1, 180)
            prefs[Keys.WIDGET_TRANSPARENCY_PERCENT] = settings.widgetTransparencyPercent.coerceIn(0, 100)
        }
    }
}
