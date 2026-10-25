package com.dio.alert

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AlertStatus(
    val isConfigured: Boolean,
    /** Null until the server's first poll after it started. */
    val isActive: Boolean?,
    val kind: String?,
    /** UTC ISO-8601; null when the state began before the server started watching. */
    val since: String?
)

/** Mind's AirAlert API. Blocking: call it off the main thread. */
object ApiClient {
    class ApiException(val code: Int, message: String) : IOException(message)

    fun getStatus(): AlertStatus {
        val json = JSONObject(request("GET", "AirAlert/GetStatus"))
        return AlertStatus(
            isConfigured = json.optBoolean("isConfigured"),
            isActive = if (json.isNull("isActive")) null else json.getBoolean("isActive"),
            kind = if (json.isNull("kind")) null else json.getString("kind"),
            since = if (json.isNull("since")) null else json.getString("since")
        )
    }

    fun registerDevice(deviceKey: String, fcmToken: String, name: String) {
        request("POST", "AirAlert/RegisterDevice", deviceKey, JSONObject().put("fcmToken", fcmToken).put("name", name))
    }

    fun unregisterDevice(deviceKey: String, fcmToken: String) {
        request("POST", "AirAlert/UnregisterDevice", deviceKey, JSONObject().put("fcmToken", fcmToken))
    }

    /** Asks the server to send a test alert to this user's phones, through FCM like a real one. */
    fun testDevice(deviceKey: String, kind: String) {
        request("POST", "AirAlert/TestDevice", deviceKey, JSONObject().put("kind", kind))
    }

    private fun request(method: String, path: String, deviceKey: String? = null, body: JSONObject? = null): String {
        val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            deviceKey?.let { connection.setRequestProperty("X-Device-Key", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
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
