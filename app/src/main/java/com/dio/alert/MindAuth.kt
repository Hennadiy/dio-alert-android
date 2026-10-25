package com.dio.alert

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Sign-in through Mind's OAuth login page (authorization code + PKCE), the same page AI
 * connectors use: it handles the password and reCAPTCHA, so the app never sees the password.
 * The page sends the code back to [REDIRECT_URI], which opens MainActivity. The access token
 * is used once, to create this phone's device key; the app keeps only that key.
 */
object MindAuth {
    const val CLIENT_ID = "dio-alert"
    // Registered for CLIENT_ID in the server's OAuth:Clients (appsettings).
    const val REDIRECT_URI = "com.dio.alert:/oauth"
    const val REGISTER_URL = "https://mind.dio-quiz.com.ua/register"

    private val serverRoot: String
        get() = BuildConfig.API_BASE_URL.removeSuffix("api/")

    /** Opens the login page in a browser tab. */
    fun startSignIn(context: Context) {
        val verifier = randomToken(48)
        val state = randomToken(16)
        Prefs(context).setPendingSignIn(verifier, state)

        val url = Uri.parse("${serverRoot}oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
        CustomTabsIntent.Builder().build().launchUrl(context, url)
    }

    fun openRegistration(context: Context) {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(REGISTER_URL))
    }

    fun isRedirect(uri: Uri?): Boolean = uri?.scheme == "com.dio.alert"

    /**
     * Finishes sign-in from the redirect: exchanges the code for a token, creates a device key
     * named after the phone and returns it with the signed-in email. Blocking.
     */
    fun completeSignIn(context: Context, redirect: Uri): Pair<String, String?> {
        val prefs = Prefs(context)
        val (verifier, state) = prefs.takePendingSignIn() ?: throw IllegalStateException("No sign-in in progress")
        // The state ties the redirect to the sign-in this app started.
        if (redirect.getQueryParameter("state") != state) {
            throw IllegalStateException("Sign-in state mismatch")
        }
        val code = redirect.getQueryParameter("code") ?: throw IllegalStateException("No authorization code")

        val accessToken = ApiClient.exchangeCode(code, verifier, REDIRECT_URI, CLIENT_ID)
        val key = ApiClient.createDeviceKey(accessToken, "Dio Alert · ${Prefs.deviceName}")
        return key to emailFromJwt(accessToken)
    }

    private fun emailFromJwt(jwt: String): String? = runCatching {
        val payload = jwt.split('.')[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        JSONObject(json).optString("email").ifEmpty { null }
    }.getOrNull()

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun challenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
