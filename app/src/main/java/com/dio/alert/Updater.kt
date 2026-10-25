package com.dio.alert

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val apkUrl: String)

/**
 * Self-update from the GitHub releases of this repo (the app is not on Google Play).
 * Android only installs an update signed with the same key as the installed app, so a
 * tampered APK can't replace it. Blocking network calls: use them off the main thread.
 */
object Updater {
    private const val TAG = "DioAlert"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/Hennadiy/dio-alert-android/releases/latest"
    private const val APK_NAME = "dio-alert.apk"

    /** The installed version, without the tag's "v" (CI names builds 1.0.<run>). */
    val installedVersion: String
        get() = BuildConfig.VERSION_NAME.removePrefix("v")

    /** The latest published release if it is newer than the installed app, else null. */
    fun findUpdate(): AppRelease? {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        val json = try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            if (connection.responseCode != 200) {
                return null
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }

        val version = json.optString("tag_name").removePrefix("v")
        val assets = json.optJSONArray("assets") ?: return null
        val apkUrl = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name") == APK_NAME }
            ?.optString("browser_download_url")
            ?: return null

        return if (isNewer(version, installedVersion)) AppRelease(version, apkUrl) else null
    }

    /** False until the user allows "install unknown apps" for Dio Alert (asked once). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Downloads the APK into the app's cache. */
    fun download(context: Context, release: AppRelease): File {
        val file = File(context.cacheDir, APK_NAME)
        // GitHub redirects asset downloads to its file host; both are https, so it is followed.
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != 200) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        return file
    }

    /**
     * Hands the APK to Android's installer; the result arrives in [InstallResultReceiver].
     * On Android 12+ Android lets an app that installed itself update itself without asking,
     * so only the first update (the app was installed from the browser) needs a confirmation.
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(apk.name, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // Mutable: the installer adds the status and, if needed, the confirmation intent.
            val callback = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(callback.intentSender)
        }
    }

    /** Compares dotted numeric versions: 1.0.12 > 1.0.9. */
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Status of an install started by [install]. */
    class InstallResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // Android wants the user to confirm: show its dialog.
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    // The app restarts as the new version; nothing to do.
                }
                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    Log.w(TAG, "Update failed ($status): $message")
                    // STATUS_FAILURE_ABORTED = the user cancelled; not worth a message.
                    if (status != PackageInstaller.STATUS_FAILURE_ABORTED) {
                        Toast.makeText(context, context.getString(R.string.update_failed, message ?: status.toString()), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
