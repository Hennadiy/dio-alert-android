package com.dio.alert

import android.app.Application
import com.google.android.recaptcha.Recaptcha
import com.google.android.recaptcha.RecaptchaAction
import com.google.android.recaptcha.RecaptchaClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * reCAPTCHA Enterprise for the app's own sign-in and registration forms: no checkbox, the SDK
 * scores the request invisibly and Mind's server checks that score (CaptchaService.VerifyAndroid).
 * The key comes from the build (repo variable RECAPTCHA_SITE_KEY); without it the forms can't work.
 */
object AppCaptcha {
    class NotConfiguredException : IllegalStateException("reCAPTCHA is not set up in this build")

    private const val TIMEOUT_MS = 10_000L

    private val mutex = Mutex()
    private var client: RecaptchaClient? = null

    suspend fun loginToken(application: Application): String = token(application, RecaptchaAction.LOGIN)

    suspend fun signupToken(application: Application): String = token(application, RecaptchaAction.SIGNUP)

    private suspend fun token(application: Application, action: RecaptchaAction): String {
        val client = client(application)
        return client.execute(action, TIMEOUT_MS).getOrThrow()
    }

    // Google asks for one client per app run.
    private suspend fun client(application: Application): RecaptchaClient = mutex.withLock {
        client ?: run {
            if (BuildConfig.RECAPTCHA_SITE_KEY.isBlank()) {
                throw NotConfiguredException()
            }
            Recaptcha.fetchClient(application, BuildConfig.RECAPTCHA_SITE_KEY).also { client = it }
        }
    }
}
