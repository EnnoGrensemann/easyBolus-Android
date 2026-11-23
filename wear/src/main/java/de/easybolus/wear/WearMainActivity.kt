package de.easybolus.wear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import java.util.Locale

/**
 * Kompakte AMOLED-Status-Ansicht auf der Smartwatch (z. B. beim Tippen auf die Complication).
 * Zeigt den aktuellen BZ, Trend, Delta, Zeitstempel, IOB & COB und erlaubt einen manuellen Sync.
 */
class WearMainActivity : Activity() {

    private lateinit var bgView: TextView
    private lateinit var trendView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var iobCobView: TextView

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderCurrentState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_main)

        bgView = findViewById(R.id.wear_bg_value)
        trendView = findViewById(R.id.wear_trend)
        subtitleView = findViewById(R.id.wear_subtitle)
        iobCobView = findViewById(R.id.wear_iob_cob)

        findViewById<Button>(R.id.wear_sync_button).setOnClickListener {
            Toast.makeText(this, getString(R.string.wear_sync_requested), Toast.LENGTH_SHORT).show()
            pullLatestFromDataLayer()
            requestSyncFromPhone()
        }

        renderCurrentState()
        pullLatestFromDataLayer()
        requestSyncFromPhone()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(WearGlucoseStore.ACTION_GLUCOSE_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateReceiver, filter)
        }
        renderCurrentState()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(updateReceiver) }
    }

    private fun renderCurrentState() {
        val snapshot = WearGlucoseStore.load(this)
        val now = System.currentTimeMillis()
        val locale = Locale.getDefault()
        val ieUnit = getString(R.string.unit_ie)

        val color = snapshot.statusColorArgb(now)
        bgView.text = snapshot.formattedBg(now, locale)
        bgView.setTextColor(color)
        trendView.text = snapshot.activeTrendSymbol(now)
        trendView.setTextColor(color)

        val minsAgo = snapshot.minutesAgo(now)
        val timeText = when (minsAgo) {
            null -> getString(R.string.wear_waiting)
            0L -> getString(R.string.wear_just_now)
            in 1L..119L -> getString(R.string.wear_minutes_ago, minsAgo)
            else -> getString(R.string.wear_hours_ago, minsAgo / 60L)
        }
        val delta = snapshot.formattedDelta(now, locale)
        subtitleView.text = if (!delta.isNullOrEmpty()) "$delta · $timeText" else timeText

        iobCobView.text = if (snapshot.hasData) {
            String.format(locale, "IOB %.2f %s · COB %.0f g", snapshot.iob, ieUnit, snapshot.cob)
        } else {
            ""
        }
    }

    private fun pullLatestFromDataLayer() {
        runCatching {
            Wearable.getDataClient(this)
                .getDataItems(Uri.parse("wear://*${WearDataListenerService.PATH_GLUCOSE}"))
                .addOnSuccessListener { items ->
                    try {
                        var found = false
                        for (item in items) {
                            if (item.uri.path == WearDataListenerService.PATH_GLUCOSE) {
                                val map = DataMapItem.fromDataItem(item).dataMap
                                WearDataListenerService.saveFromDataMap(applicationContext, map)
                                found = true
                            }
                        }
                        if (found) {
                            WearDataListenerService.notifyAllWatchSurfaces(applicationContext)
                            renderCurrentState()
                        }
                    } finally {
                        items.release()
                    }
                }
        }
    }

    private fun requestSyncFromPhone() {
        runCatching {
            Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
                val msgClient = Wearable.getMessageClient(this)
                for (node in nodes) {
                    msgClient.sendMessage(node.id, WearDataListenerService.PATH_REQUEST_SYNC, ByteArray(0))
                }
            }
        }
    }
}
