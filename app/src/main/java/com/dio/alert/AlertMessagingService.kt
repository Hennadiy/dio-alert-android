package com.dio.alert

import android.os.PowerManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Alerts from Mind are data-only, high-priority FCM messages: Android wakes the app for each
 * one, here, whether it is open or not. The app shows the notification (its channel vibrates)
 * and plays the kind's sound on the alarm stream, waiting until it ends so the app isn't put
 * back to sleep mid-siren.
 */
class AlertMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val kind = message.data["kind"] ?: return
        message.data["sentAt"]?.toLongOrNull()?.let { sentAt ->
            val now = System.currentTimeMillis()
            // Screen off is the real test: that's when the phone saves battery by holding messages back.
            val screenOff = !getSystemService(PowerManager::class.java).isInteractive
            Prefs(this).lastDelivery = Prefs.Delivery(now, (now - sentAt).coerceAtLeast(0), screenOff)
        }
        val region = message.data["region"]?.let { Region(0, it, message.data["regionEn"] ?: it) } ?: Prefs(this).region
        AlertNotifier.show(this, kind, region, message.data["minutes"]?.toIntOrNull(), message.data["test"] == "1")
        AlertSound.playAndWait(this, kind)
    }

    // Firebase rotates tokens now and then; the server only knows the one we registered.
    // Runs on a background thread, so the blocking call is fine here.
    override fun onNewToken(token: String) {
        val prefs = Prefs(this)
        val key = prefs.deviceKey ?: return
        try {
            ApiClient.registerDevice(key, token, Prefs.deviceName, prefs.region.uid)
            prefs.registeredToken = token
        } catch (e: Exception) {
            // The app registers again the next time it is opened.
            Log.w(TAG, "Could not register the new FCM token", e)
        }
    }

    private companion object {
        const val TAG = "DioAlert"
    }
}
