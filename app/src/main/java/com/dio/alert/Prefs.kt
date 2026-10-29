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

    /** The PKCE verifier and state of a sign-in in progress (survive the activity being recreated). */
    fun setPendingSignIn(verifier: String, state: String) {
        prefs.edit().putString(KEY_PENDING_VERIFIER, verifier).putString(KEY_PENDING_STATE, state).apply()
    }

    fun takePendingSignIn(): Pair<String, String>? {
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null) ?: return null
        val state = prefs.getString(KEY_PENDING_STATE, null) ?: return null
        prefs.edit().remove(KEY_PENDING_VERIFIER).remove(KEY_PENDING_STATE).apply()
        return verifier to state
    }

    /** Forgets the connection (not the pending sign-in). */
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
        private const val KEY_PENDING_VERIFIER = "pending_verifier"
        private const val KEY_PENDING_STATE = "pending_state"

        /** Shown in Mind's list of connected phones. */
        val deviceName: String
            get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }
}
