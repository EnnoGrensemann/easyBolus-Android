package de.easybolus.data.juggluco

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.easybolus.data.local.EasyBolusDatabase
import de.easybolus.data.settings.SettingsRepository
import de.easybolus.notification.EasyBolusNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JugglucoBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        if (action != JugglucoBroadcastParser.ACTION_GLUCODATA_MINUTE &&
            action != JugglucoBroadcastParser.ACTION_XDRIP_BG_ESTIMATE
        ) {
            return
        }

        val extras = extractExtras(intent)
        val reading = JugglucoBroadcastParser.parse(extras) ?: return

        val pendingResult = goAsync()
        scope.launch {
            try {
                if (SettingsRepository(context).isJugglucoEnabled()) {
                    EasyBolusDatabase.getInstance(context).dao().insertGlucoseReading(reading)
                    EasyBolusNotifier.refreshAll(context, evaluateCorrection = true)
                    de.easybolus.data.sync.DriveSyncManager.scheduleAutoSyncAsync(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun extractExtras(intent: Intent): JugglucoBroadcastParser.RawBroadcastExtras {
        val bundle = intent.extras
        val bg: Double? = when {
            bundle == null -> null
            bundle.containsKey("com.eveningoutpost.dexdrip.Extras.BgEstimate") ->
                readNumeric(bundle.get("com.eveningoutpost.dexdrip.Extras.BgEstimate"))
            bundle.containsKey("glucosemgdl") ->
                readNumeric(bundle.get("glucosemgdl"))
            bundle.containsKey("glucose") ->
                readNumeric(bundle.get("glucose"))
            else -> null
        }

        val rate: Double? = when {
            bundle == null -> null
            bundle.containsKey("rate") -> readNumeric(bundle.get("rate"))
            bundle.containsKey("BgSlope") -> readNumeric(bundle.get("BgSlope"))
            else -> null
        }

        val slopeName: String? = bundle?.getString("com.eveningoutpost.dexdrip.Extras.BgSlopeName")

        val time: Long? = when {
            bundle == null -> null
            bundle.containsKey("com.eveningoutpost.dexdrip.Extras.Time") ->
                readLong(bundle.get("com.eveningoutpost.dexdrip.Extras.Time"))
            bundle.containsKey("time") ->
                readLong(bundle.get("time"))
            else -> null
        }

        return JugglucoBroadcastParser.RawBroadcastExtras(
            action = intent.action,
            glucoseMgDl = bg,
            ratePerMin = rate,
            slopeName = slopeName,
            timestampMillis = time
        )
    }

    private fun readNumeric(value: Any?): Double? = when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Int -> value.toDouble()
        is Long -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun readLong(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}
