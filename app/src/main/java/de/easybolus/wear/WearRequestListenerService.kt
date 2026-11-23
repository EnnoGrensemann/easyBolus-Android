package de.easybolus.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import de.easybolus.notification.EasyBolusNotifier

/**
 * Reagiert auf manuelle Sync-Anfragen von der Wear-OS-Uhr und sendet sofort frische Daten zurück.
 */
class WearRequestListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearDataSync.PATH_REQUEST_SYNC) {
            EasyBolusNotifier.refreshAllAsync(applicationContext, evaluateCorrection = false)
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}
