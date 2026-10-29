package com.dio.alert

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Alerts from Mind are FCM notification messages naming the channel of the alert kind:
 * with the app in the background Android shows them itself, without this service. While
 * the app is open Android hands them here instead, and the app shows the same notification.
 */
class AlertMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val kind = message.data["kind"] ?: return
        val region = message.data["region"]?.let { Region(0, it, message.data["regionEn"] ?: it) } ?: Prefs(this).region
        AlertNotifier.show(this, kind, region, message.data["minutes"]?.toIntOrNull(), message.data["test"] == "1")
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
