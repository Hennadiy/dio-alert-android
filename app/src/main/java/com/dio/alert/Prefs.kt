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

    /** Set when connected by signing in; null when a key was pasted. */
    var userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    /** The app created the key on sign-in, so signing out revokes it. A pasted key is the user's to keep. */
    var keyCreatedByApp: Boolean
        get() = prefs.getBoolean(KEY_CREATED_BY_APP, false)
        set(value) = prefs.edit().putBoolean(KEY_CREATED_BY_APP, value).apply()

    /** The region whose alerts this phone gets (kept when signing out). */
    var region: Region
        get() = prefs.getString(KEY_REGION, null)?.let { runCatching { Region.fromJson(JSONObject(it)) }.getOrNull() } ?: Region.DEFAULT
        set(value) = prefs.edit().putString(KEY_REGION, value.toJson().toString()).apply()

    /** The last list of regions from Mind, so the picker works offline. */
    var regions: List<Region>
        get() = prefs.getString(KEY_REGIONS, null)?.let { runCatching { Region.listFromJson(JSONArray(it)) }.getOrNull() } ?: emptyList()
        set(value) = prefs.edit().putString(KEY_REGIONS, JSONArray(value.map { it.toJson() }).toString()).apply()

    val isConnected: Boolean
        get() = !deviceKey.isNullOrEmpty()

    /** Forgets the connection. */
    fun clearConnection() {
        prefs.edit()
            .remove(KEY_DEVICE_KEY)
            .remove(KEY_TOKEN)
            .remove(KEY_EMAIL)
            .remove(KEY_CREATED_BY_APP)
            .apply()
    }

    companion object {
        private const val KEY_DEVICE_KEY = "device_key"
        private const val KEY_TOKEN = "fcm_token"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_CREATED_BY_APP = "key_created_by_app"
        private const val KEY_REGION = "region"
        private const val KEY_REGIONS = "regions"

        /** Shown in Mind's list of connected phones. */
        val deviceName: String
            get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
}
