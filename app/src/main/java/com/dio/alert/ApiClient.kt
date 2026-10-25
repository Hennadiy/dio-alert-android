package com.dio.alert

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AlertStatus(
    val isConfigured: Boolean,
    /** Null until the server's first poll after it started. */
    val isActive: Boolean?,
    val kind: String?,
    /** UTC ISO-8601; null when the state began before the server started watching. */
    val since: String?
)

/** Mind's API. Blocking: call it off the main thread. */
object ApiClient {
    class ApiException(val code: Int, message: String) : IOException(message)

    private val serverRoot: String
        get() = BuildConfig.API_BASE_URL.removeSuffix("api/")

    fun getStatus(): AlertStatus {
        val json = JSONObject(request("GET", BuildConfig.API_BASE_URL + "AirAlert/GetStatus"))
        return AlertStatus(
            isConfigured = json.optBoolean("isConfigured"),
            isActive = if (json.isNull("isActive")) null else json.getBoolean("isActive"),
            kind = if (json.isNull("kind")) null else json.getString("kind"),
            since = if (json.isNull("since")) null else json.getString("since")
        )
    }

    fun registerDevice(deviceKey: String, fcmToken: String, name: String) {
        postJson("AirAlert/RegisterDevice", deviceKey, JSONObject().put("fcmToken", fcmToken).put("name", name))
    }

    /** [revokeKey] also deletes the device key on the server (for keys the app created on sign-in). */
    fun unregisterDevice(deviceKey: String, fcmToken: String?, revokeKey: Boolean) {
        postJson("AirAlert/UnregisterDevice", deviceKey, JSONObject().put("fcmToken", fcmToken ?: "").put("revokeKey", revokeKey))
    }

    /** Asks the server to send a test alert to this user's phones, through FCM like a real one. */
    fun testDevice(deviceKey: String, kind: String) {
        postJson("AirAlert/TestDevice", deviceKey, JSONObject().put("kind", kind))
    }

    /** OAuth authorization code → access token (the refresh token isn't needed: the token is used once). */
    fun exchangeCode(code: String, verifier: String, redirectUri: String, clientId: String): String {
        val form = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "client_id" to clientId,
            "code_verifier" to verifier
        ).entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

        val body = request("POST", serverRoot + "oauth/token", body = form, contentType = "application/x-www-form-urlencoded")
        return JSONObject(body).getString("access_token")
    }

    /** Creates a device key for the signed-in user and returns the plain key (shown only once). */
    fun createDeviceKey(accessToken: String, name: String): String {
        val body = request(
            "POST", BuildConfig.API_BASE_URL + "DeviceKey/Create",
            headers = mapOf("Authorization" to "Bearer $accessToken"),
            body = JSONObject().put("name", name).toString(),
            contentType = "application/json; charset=utf-8"
        )
        return JSONObject(body).getString("key")
    }

    private fun postJson(path: String, deviceKey: String, body: JSONObject) {
        request(
            "POST", BuildConfig.API_BASE_URL + path,
            headers = mapOf("X-Device-Key" to deviceKey),
            body = body.toString(),
            contentType = "application/json; charset=utf-8"
        )
    }

    private fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: String? = null
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // The API answers errors with the message as the body (sometimes JSON-quoted).
                throw ApiException(code, text.trim().trim('"').ifBlank { "HTTP $code" })
            }
            return text
        } finally {
            connection.disconnect()
        }
    }
}
