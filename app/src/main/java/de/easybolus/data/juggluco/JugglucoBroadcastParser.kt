package de.easybolus.data.juggluco

import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.local.TrendArrow

/**
 * Reines Kotlin-Parsing für Juggluco-Broadcasts:
 * - Action "glucodata.Minute": Extras "glucose" / "glucosemgdl" (Int/Float/Double), "rate" (Float/Double), "time" (Long, Sek. oder ms)
 * - Action "com.eveningoutpost.dexdrip.BgEstimate": Extras "com.eveningoutpost.dexdrip.Extras.BgEstimate" (Double), "com.eveningoutpost.dexdrip.Extras.Time" (Long), "com.eveningoutpost.dexdrip.Extras.BgSlopeName" (String)
 */
object JugglucoBroadcastParser {

    const val ACTION_GLUCODATA_MINUTE = "glucodata.Minute"
    const val ACTION_XDRIP_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"

    data class RawBroadcastExtras(
        val action: String?,
        val glucoseMgDl: Double? = null,
        val ratePerMin: Double? = null,
        val slopeName: String? = null,
        val timestampMillis: Long? = null,
        val nowMillis: Long = System.currentTimeMillis()
    )

    fun parse(extras: RawBroadcastExtras): GlucoseReadingEntity? {
        val bg = extras.glucoseMgDl ?: return null
        if (bg <= 0.0 || bg > 1000.0) return null

        val ts = normalizeTimestamp(extras.timestampMillis, extras.nowMillis)
        val trend = when {
            extras.ratePerMin != null -> TrendArrow.fromRate(extras.ratePerMin)
            !extras.slopeName.isNullOrBlank() -> TrendArrow.fromSlopeName(extras.slopeName)
            else -> TrendArrow.FLAT
        }

        val source = when (extras.action) {
            ACTION_XDRIP_BG_ESTIMATE -> "Juggluco (xDrip)"
            else -> "Juggluco"
        }

        return GlucoseReadingEntity(
            timestampMillis = ts,
            mgDl = bg.toInt(),
            trendArrow = trend,
            deltaMgDl = extras.ratePerMin ?: 0.0,
            source = source
        )
    }

    private fun normalizeTimestamp(raw: Long?, fallbackNow: Long): Long {
        if (raw == null || raw <= 0L) return fallbackNow
        // Juggluco sendet teils Sekunden (10-stellig) statt Millisekunden (13-stellig)
        return if (raw < 10_000_000_000L) raw * 1000L else raw
    }
}
