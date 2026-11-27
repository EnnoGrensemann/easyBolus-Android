package de.easybolus.domain

import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.CarbFactorMode
import de.easybolus.data.settings.GlucoseUnit
import de.easybolus.data.settings.InsulinType
import de.easybolus.data.settings.ThemeMode
import de.easybolus.data.settings.UserSettings
import org.json.JSONArray
import org.json.JSONObject

data class FullBackupPayload(
    val settings: UserSettings?,
    val readings: List<GlucoseReadingEntity>,
    val entries: List<DiaryEntryEntity>
)

object BackupManager {

    private const val FORMAT_VERSION = 1

    fun serializeSettings(settings: UserSettings): String {
        val root = JSONObject().apply {
            put("type", "easybolus_settings")
            put("version", FORMAT_VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put("settings", settingsToJson(settings))
        }
        return root.toString(2)
    }

    fun deserializeSettings(jsonString: String): UserSettings? {
        return runCatching {
            val root = JSONObject(jsonString)
            val obj = if (root.has("settings")) root.getJSONObject("settings") else root
            jsonToSettings(obj)
        }.getOrNull()
    }

    fun serializeFullBackup(
        settings: UserSettings,
        readings: List<GlucoseReadingEntity>,
        entries: List<DiaryEntryEntity>
    ): String {
        val readingsArray = JSONArray()
        for (r in readings) {
            readingsArray.put(
                JSONObject().apply {
                    put("timestampMillis", r.timestampMillis)
                    put("mgDl", r.mgDl)
                    put("trendArrow", r.trendArrow.name)
                    put("deltaMgDl", r.deltaMgDl)
                    put("source", r.source)
                }
            )
        }

        val entriesArray = JSONArray()
        for (e in entries) {
            entriesArray.put(
                JSONObject().apply {
                    put("timestampMillis", e.timestampMillis)
                    put("type", e.type.name)
                    if (e.bolusUnits != null) put("bolusUnits", e.bolusUnits)
                    if (e.carbsGrams != null) put("carbsGrams", e.carbsGrams)
                    if (e.basalUnits != null) put("basalUnits", e.basalUnits)
                    if (e.note != null) put("note", e.note)
                    if (e.bgAtTimeMgDl != null) put("bgAtTimeMgDl", e.bgAtTimeMgDl)
                    if (e.trendAtTime != null) put("trendAtTime", e.trendAtTime.name)
                }
            )
        }

        val root = JSONObject().apply {
            put("type", "easybolus_full_backup")
            put("version", FORMAT_VERSION)
            put("exportedAtMillis", System.currentTimeMillis())
            put("settings", settingsToJson(settings))
            put("glucoseReadings", readingsArray)
            put("diaryEntries", entriesArray)
        }
        return root.toString(2)
    }

    fun deserializeFullBackup(jsonString: String): FullBackupPayload? {
        return runCatching {
            val root = JSONObject(jsonString)
            val settings = if (root.has("settings")) jsonToSettings(root.getJSONObject("settings")) else null

            val readings = mutableListOf<GlucoseReadingEntity>()
            val rArr = root.optJSONArray("glucoseReadings")
            if (rArr != null) {
                for (i in 0 until rArr.length()) {
                    val obj = rArr.getJSONObject(i)
                    val trend = TrendArrow.entries.firstOrNull { it.name == obj.optString("trendArrow") }
                        ?: TrendArrow.FLAT
                    readings.add(
                        GlucoseReadingEntity(
                            timestampMillis = obj.getLong("timestampMillis"),
                            mgDl = obj.getInt("mgDl"),
                            trendArrow = trend,
                            deltaMgDl = obj.optDouble("deltaMgDl", 0.0),
                            source = obj.optString("source", "Backup")
                        )
                    )
                }
            }

            val entries = mutableListOf<DiaryEntryEntity>()
            val eArr = root.optJSONArray("diaryEntries")
            if (eArr != null) {
                for (i in 0 until eArr.length()) {
                    val obj = eArr.getJSONObject(i)
                    val type = DiaryEntryType.entries.firstOrNull { it.name == obj.optString("type") }
                        ?: continue
                    val trend = if (obj.has("trendAtTime")) {
                        TrendArrow.entries.firstOrNull { it.name == obj.optString("trendAtTime") }
                    } else null
                    entries.add(
                        DiaryEntryEntity(
                            timestampMillis = obj.getLong("timestampMillis"),
                            type = type,
                            bolusUnits = if (obj.has("bolusUnits")) obj.getDouble("bolusUnits") else null,
                            carbsGrams = if (obj.has("carbsGrams")) obj.getDouble("carbsGrams") else null,
                            basalUnits = if (obj.has("basalUnits")) obj.getDouble("basalUnits") else null,
                            note = if (obj.has("note")) obj.optString("note").takeIf { it.isNotBlank() } else null,
                            bgAtTimeMgDl = if (obj.has("bgAtTimeMgDl")) obj.getInt("bgAtTimeMgDl") else null,
                            trendAtTime = trend
                        )
                    )
                }
            }

            FullBackupPayload(settings = settings, readings = readings, entries = entries)
        }.getOrNull()
    }

    private fun settingsToJson(s: UserSettings): JSONObject = JSONObject().apply {
        put("carbRatio", s.carbRatio)
        put("carbFactorMode", s.carbFactorMode.name)
        put("useHourlyCarbRatio", s.useHourlyCarbRatio)
        put("hourlyCarbRatios", JSONArray(s.hourlyCarbRatios))
        put("isf", s.isf)
        put("targetBg", s.targetBg)
        put("targetRangeMin", s.targetRangeMin)
        put("targetRangeMax", s.targetRangeMax)
        put("diaHours", s.diaHours)
        put("insulinType", s.insulinType.name)
        put("penStepSize", s.penStepSize)
        put("defaultBasalUnits", s.defaultBasalUnits)
        put("carbAbsorptionHours", s.carbAbsorptionHours)
        put("themeMode", s.themeMode.name)
        put("glucoseUnit", s.glucoseUnit.name)
        put("jugglucoEnabled", s.jugglucoEnabled)
        put("persistentNotificationEnabled", s.persistentNotificationEnabled)
        put("correctionAlertEnabled", s.correctionAlertEnabled)
        put("correctionAlertMinUnits", s.correctionAlertMinUnits)
        put("correctionAlertWaitMinutes", s.correctionAlertWaitMinutes)
        put("widgetTransparencyPercent", s.widgetTransparencyPercent)
        put("defaultSplitPart1Percent", s.defaultSplitPart1Percent)
    }

    private fun jsonToSettings(obj: JSONObject): UserSettings {
        val baseCarbRatio = obj.optDouble("carbRatio", 10.0).coerceAtLeast(0.5)
        val hourlyArr = obj.optJSONArray("hourlyCarbRatios")
        val hourlyList = if (hourlyArr != null && hourlyArr.length() == 24) {
            List(24) { idx -> hourlyArr.optDouble(idx, baseCarbRatio).coerceAtLeast(0.5) }
        } else {
            List(24) { baseCarbRatio }
        }
        return UserSettings(
            carbRatio = baseCarbRatio,
            carbFactorMode = CarbFactorMode.entries.firstOrNull { it.name == obj.optString("carbFactorMode") }
                ?: CarbFactorMode.GRAMS_PER_IE,
            useHourlyCarbRatio = obj.optBoolean("useHourlyCarbRatio", false),
            hourlyCarbRatios = hourlyList,
            isf = obj.optDouble("isf", 40.0).coerceAtLeast(1.0),
            targetBg = obj.optInt("targetBg", 100).coerceIn(60, 200),
            targetRangeMin = obj.optInt("targetRangeMin", 80).coerceIn(50, 140),
            targetRangeMax = obj.optInt("targetRangeMax", 180).coerceIn(120, 300),
            diaHours = obj.optDouble("diaHours", 4.0).coerceIn(2.0, 8.0),
            insulinType = InsulinType.entries.firstOrNull { it.name == obj.optString("insulinType") }
                ?: InsulinType.RAPID,
            penStepSize = if (obj.optDouble("penStepSize", 0.5) >= 1.0) 1.0 else 0.5,
            defaultBasalUnits = obj.optDouble("defaultBasalUnits", 0.0).coerceIn(0.0, 200.0),
            carbAbsorptionHours = obj.optDouble("carbAbsorptionHours", 3.0).coerceIn(1.0, 6.0),
            themeMode = ThemeMode.entries.firstOrNull { it.name == obj.optString("themeMode") }
                ?: ThemeMode.SYSTEM,
            glucoseUnit = GlucoseUnit.entries.firstOrNull { it.name == obj.optString("glucoseUnit") }
                ?: GlucoseUnit.MG_DL,
            jugglucoEnabled = obj.optBoolean("jugglucoEnabled", true),
            persistentNotificationEnabled = obj.optBoolean("persistentNotificationEnabled", false),
            correctionAlertEnabled = obj.optBoolean("correctionAlertEnabled", false),
            correctionAlertMinUnits = obj.optDouble("correctionAlertMinUnits", 1.5).coerceIn(0.5, 20.0),
            correctionAlertWaitMinutes = obj.optInt("correctionAlertWaitMinutes", 20).coerceIn(1, 180),
            widgetTransparencyPercent = obj.optInt("widgetTransparencyPercent", 0).coerceIn(0, 100),
            defaultSplitPart1Percent = obj.optInt("defaultSplitPart1Percent", 60).coerceIn(10, 90)
        )
    }
}
