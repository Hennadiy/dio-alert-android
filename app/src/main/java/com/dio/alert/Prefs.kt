package com.dio.alert

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** The connection to Mind: the device key and the FCM token last registered with it. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("dio_alert", Context.MODE_PRIVATE)

    var deviceKey: String?
        get() = prefs.getString(KEY_DEVICE_KEY, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_KEY, value).apply()

    var registeredToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** Whom the app is signed in as. */
    var userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /** The region whose alerts this phone gets (kept when signing out). */
    var region: Region
        get() = prefs.getString(KEY_REGION, null)?.let { runCatching { Region.fromJson(JSONObject(it)) }.getOrNull() } ?: Region.DEFAULT
        set(value) = prefs.edit().putString(KEY_REGION, value.toJson().toString()).apply()

    /** The last list of regions from Mind, so the picker works offline. */
    var regions: List<Region>
        get() = prefs.getString(KEY_REGIONS, null)?.let { runCatching { Region.listFromJson(JSONArray(it)) }.getOrNull() } ?: emptyList()
        set(value) = prefs.edit().putString(KEY_REGIONS, JSONArray(value.map { it.toJson() }).toString()).apply()

    /** The last alert to arrive: when, how long it took from Mind, and whether the screen was off. */
    data class Delivery(val at: Long, val delayMs: Long, val screenOff: Boolean)

    var lastDelivery: Delivery?
        get() = if (prefs.contains(KEY_DELIVERED_AT)) Delivery(
            prefs.getLong(KEY_DELIVERED_AT, 0),
            prefs.getLong(KEY_DELIVERY_DELAY, 0),
            prefs.getBoolean(KEY_DELIVERY_SCREEN_OFF, false)
        ) else null
        set(value) {
            val edit = prefs.edit()
            if (value == null) edit.remove(KEY_DELIVERED_AT).remove(KEY_DELIVERY_DELAY).remove(KEY_DELIVERY_SCREEN_OFF)
            else edit.putLong(KEY_DELIVERED_AT, value.at).putLong(KEY_DELIVERY_DELAY, value.delayMs).putBoolean(KEY_DELIVERY_SCREEN_OFF, value.screenOff)
            edit.apply()
        }

    val isConnected: Boolean
        get() = !deviceKey.isNullOrEmpty()

    /** Forgets the connection. */
    fun clearConnection() {
        prefs.edit()
            .remove(KEY_DEVICE_KEY)
            .remove(KEY_TOKEN)
            .remove(KEY_EMAIL)
            .apply()
    }

    companion object {
        private const val KEY_DEVICE_KEY = "device_key"
        private const val KEY_TOKEN = "fcm_token"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_REGION = "region"
        private const val KEY_DELIVERED_AT = "delivered_at"
        private const val KEY_DELIVERY_DELAY = "delivery_delay"
        private const val KEY_DELIVERY_SCREEN_OFF = "delivery_screen_off"
        private const val KEY_REGIONS = "regions"

        /** Shown in Mind's list of connected phones. */
        val deviceName: String
            get() {
                // Some makers repeat themselves in the model ("motorola edge 70 fusion").
                val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL
                else "${Build.MANUFACTURER} ${Build.MODEL}"
                return model.replaceFirstChar { it.uppercase() }
            }
    }
}
