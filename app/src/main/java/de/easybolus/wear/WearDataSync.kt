package de.easybolus.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import de.easybolus.data.local.GlucoseReadingEntity
import de.easybolus.data.local.TrendArrow
import de.easybolus.data.settings.UserSettings

/**
 * Synchronisiert den aktuellen Blutzucker, Trend, Delta, IOB und COB per Wearable Data Layer API
 * im Hintergrund an eine verbundene Wear-OS-Smartwatch (z. B. Samsung Galaxy Watch 6).
 */
object WearDataSync {

    const val PATH_GLUCOSE = "/easybolus/glucose"
    const val PATH_REQUEST_SYNC = "/easybolus/request_sync"

    fun syncNow(
        context: Context,
        latest: GlucoseReadingEntity?,
        deltaMgDl: Int?,
        iob: Double,
        cob: Double,
        settings: UserSettings
    ) {
        runCatching {
            val request = PutDataMapRequest.create(PATH_GLUCOSE).apply {
                dataMap.putBoolean("hasData", latest != null)
                dataMap.putInt("mgDl", latest?.mgDl ?: 0)
                dataMap.putString(
                    "trendSymbol",
                    latest?.trendArrow?.takeIf { it != TrendArrow.UNKNOWN }?.symbol ?: ""
                )
                dataMap.putString("trendName", (latest?.trendArrow ?: TrendArrow.UNKNOWN).name)
                dataMap.putBoolean("hasDelta", deltaMgDl != null)
                dataMap.putInt("deltaMgDl", deltaMgDl ?: 0)
                dataMap.putLong("readingTimestampMillis", latest?.timestampMillis ?: 0L)
                dataMap.putDouble("iob", iob)
                dataMap.putDouble("cob", cob)
                dataMap.putString("unit", settings.glucoseUnit.name)
                dataMap.putInt("targetRangeMin", settings.targetRangeMin)
                dataMap.putInt("targetRangeMax", settings.targetRangeMax)
                dataMap.putBoolean("jugglucoEnabled", settings.jugglucoEnabled)
                dataMap.putLong("syncedAtMillis", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context.applicationContext).putDataItem(request)
        }
    }
}
