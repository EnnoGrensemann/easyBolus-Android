package de.easybolus.wear

import android.content.Context
import java.util.Locale
import kotlin.math.abs

data class WearGlucoseSnapshot(
    val hasData: Boolean = false,
    val mgDl: Int = 0,
    val trendSymbol: String = "",
    val trendName: String = "UNKNOWN",
    val hasDelta: Boolean = false,
    val deltaMgDl: Int = 0,
    val readingTimestampMillis: Long = 0L,
    val iob: Double = 0.0,
    val cob: Double = 0.0,
    val isMmol: Boolean = false,
    val targetRangeMin: Int = 80,
    val targetRangeMax: Int = 180,
    val jugglucoEnabled: Boolean = true
) {
    fun minutesAgo(nowMillis: Long = System.currentTimeMillis()): Long? {
        if (!hasData || readingTimestampMillis <= 0L) return null
        return ((nowMillis - readingTimestampMillis) / 60_000L).coerceAtLeast(0L)
    }

    fun isStale(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!hasData) return true
        val mins = minutesAgo(nowMillis) ?: return true
        return jugglucoEnabled && mins > 15L
    }

    fun formattedBg(nowMillis: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
        if (!hasData || isStale(nowMillis)) return "---"
        return if (isMmol) {
            String.format(locale, "%.1f", mgDl / 18.0182)
        } else {
            mgDl.toString()
        }
    }

    fun activeTrendSymbol(nowMillis: Long = System.currentTimeMillis()): String {
        if (!hasData || isStale(nowMillis)) return ""
        return trendSymbol
    }

    fun formattedBgWithTrend(nowMillis: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
        val bg = formattedBg(nowMillis, locale)
        val arrow = activeTrendSymbol(nowMillis)
        return if (arrow.isNotEmpty()) "$bg $arrow" else bg
    }

    fun formattedDelta(nowMillis: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String? {
        if (!hasData || isStale(nowMillis) || !hasDelta) return null
        val signed = if (isMmol) {
            val d = deltaMgDl / 18.0182
            if (d >= 0.0) String.format(locale, "+%.1f", abs(d))
            else String.format(locale, "-%.1f", abs(d))
        } else {
            if (deltaMgDl >= 0) "+$deltaMgDl" else deltaMgDl.toString()
        }
        return "Δ $signed"
    }

    fun statusColorArgb(nowMillis: Long = System.currentTimeMillis()): Int {
        return when {
            !hasData || isStale(nowMillis) -> COLOR_MUTED
            mgDl < targetRangeMin -> COLOR_LOW
            mgDl > targetRangeMax -> COLOR_HIGH
            else -> COLOR_IN_RANGE
        }
    }

    companion object {
        const val COLOR_IN_RANGE = 0xFF00A884.toInt()
        const val COLOR_HIGH = 0xFFFFB300.toInt()
        const val COLOR_LOW = 0xFFFF1744.toInt()
        const val COLOR_MUTED = 0xFF9AA0A6.toInt()
    }
}

object WearGlucoseStore {

    private const val PREFS_NAME = "easybolus_wear_glucose"
    const val ACTION_GLUCOSE_UPDATED = "de.easybolus.wear.ACTION_GLUCOSE_UPDATED"

    fun save(context: Context, snapshot: WearGlucoseSnapshot) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("hasData", snapshot.hasData)
            .putInt("mgDl", snapshot.mgDl)
            .putString("trendSymbol", snapshot.trendSymbol)
            .putString("trendName", snapshot.trendName)
            .putBoolean("hasDelta", snapshot.hasDelta)
            .putInt("deltaMgDl", snapshot.deltaMgDl)
            .putLong("readingTimestampMillis", snapshot.readingTimestampMillis)
            .putFloat("iob", snapshot.iob.toFloat())
            .putFloat("cob", snapshot.cob.toFloat())
            .putBoolean("isMmol", snapshot.isMmol)
            .putInt("targetRangeMin", snapshot.targetRangeMin)
            .putInt("targetRangeMax", snapshot.targetRangeMax)
            .putBoolean("jugglucoEnabled", snapshot.jugglucoEnabled)
            .apply()
    }

    fun load(context: Context): WearGlucoseSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WearGlucoseSnapshot(
            hasData = prefs.getBoolean("hasData", false),
            mgDl = prefs.getInt("mgDl", 0),
            trendSymbol = prefs.getString("trendSymbol", "").orEmpty(),
            trendName = prefs.getString("trendName", "UNKNOWN").orEmpty(),
            hasDelta = prefs.getBoolean("hasDelta", false),
            deltaMgDl = prefs.getInt("deltaMgDl", 0),
            readingTimestampMillis = prefs.getLong("readingTimestampMillis", 0L),
            iob = prefs.getFloat("iob", 0f).toDouble(),
            cob = prefs.getFloat("cob", 0f).toDouble(),
            isMmol = prefs.getBoolean("isMmol", false),
            targetRangeMin = prefs.getInt("targetRangeMin", 80),
            targetRangeMax = prefs.getInt("targetRangeMax", 180),
            jugglucoEnabled = prefs.getBoolean("jugglucoEnabled", true)
        )
    }
}
