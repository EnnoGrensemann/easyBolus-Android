package de.easybolus.wear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import de.easybolus.wear.complication.GlucoseComplicationService
import de.easybolus.wear.complication.IobCobComplicationService

/**
 * Empfängt Live-Glukosewerte vom Handy, speichert sie lokal auf der Uhr und aktualisiert
 * sofort alle aktiven easyBOLUS-Complications auf dem Zifferblatt.
 */
class WearDataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        var updated = false
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == PATH_GLUCOSE) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                saveFromDataMap(applicationContext, map)
                updated = true
            }
        }
        if (updated) {
            notifyAllWatchSurfaces(applicationContext)
        }
    }

    companion object {
        const val PATH_GLUCOSE = "/easybolus/glucose"
        const val PATH_REQUEST_SYNC = "/easybolus/request_sync"

        fun saveFromDataMap(context: Context, map: DataMap) {
            val snapshot = WearGlucoseSnapshot(
                hasData = map.getBoolean("hasData", false),
                mgDl = map.getInt("mgDl", 0),
                trendSymbol = map.getString("trendSymbol", ""),
                trendName = map.getString("trendName", "UNKNOWN"),
                hasDelta = map.getBoolean("hasDelta", false),
                deltaMgDl = map.getInt("deltaMgDl", 0),
                readingTimestampMillis = map.getLong("readingTimestampMillis", 0L),
                iob = map.getDouble("iob", 0.0),
                cob = map.getDouble("cob", 0.0),
                isMmol = map.getString("unit", "MG_DL") == "MMOL_L",
                targetRangeMin = map.getInt("targetRangeMin", 80),
                targetRangeMax = map.getInt("targetRangeMax", 180),
                jugglucoEnabled = map.getBoolean("jugglucoEnabled", true)
            )
            WearGlucoseStore.save(context, snapshot)
        }

        fun notifyAllWatchSurfaces(context: Context) {
            runCatching {
                ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, GlucoseComplicationService::class.java)
                ).requestUpdateAll()
            }
            runCatching {
                ComplicationDataSourceUpdateRequester.create(
                    context,
                    ComponentName(context, IobCobComplicationService::class.java)
                ).requestUpdateAll()
            }
            context.sendBroadcast(
                Intent(WearGlucoseStore.ACTION_GLUCOSE_UPDATED).setPackage(context.packageName)
            )
        }
    }
}
