package de.easybolus

import android.app.Application
import android.content.IntentFilter
import android.os.Build
import de.easybolus.data.juggluco.JugglucoBroadcastParser
import de.easybolus.data.juggluco.JugglucoBroadcastReceiver
import de.easybolus.notification.EasyBolusNotifier

class EasyBolusApp : Application() {

    private val jugglucoReceiver = JugglucoBroadcastReceiver()

    override fun onCreate() {
        super.onCreate()
        EasyBolusNotifier.ensureChannels(this)
        EasyBolusNotifier.refreshAllAsync(this)

        val filter = IntentFilter().apply {
            addAction(JugglucoBroadcastParser.ACTION_GLUCODATA_MINUTE)
            addAction(JugglucoBroadcastParser.ACTION_XDRIP_BG_ESTIMATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(jugglucoReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(jugglucoReceiver, filter)
        }
    }
}
