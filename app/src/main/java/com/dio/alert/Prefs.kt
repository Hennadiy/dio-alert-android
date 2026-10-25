package com.dio.alert

import android.content.Context
import android.os.Build

/** The connection to Mind: the device key and the FCM token last registered with it. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("dio_alert", Context.MODE_PRIVATE)

    var deviceKey: String?
        get() = prefs.getString(KEY_DEVICE_KEY, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_KEY, value).apply()

    var registeredToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    val isConnected: Boolean
        get() = !deviceKey.isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DEVICE_KEY = "device_key"
        private const val KEY_TOKEN = "fcm_token"

        /** Shown in Mind's list of connected phones. */
        val deviceName: String
            get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
}
