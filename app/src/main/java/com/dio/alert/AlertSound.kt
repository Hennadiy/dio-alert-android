package com.dio.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Plays an alert's sound itself, on the alarm stream: the alarm volume, heard with the ringer on
 * silent or vibrate, and let through Do Not Disturb by default. Notification-channel sounds were
 * silent on some phones (Motorola), so the channels carry only the vibration and this plays the sound.
 */
object AlertSound {
    private const val TAG = "DioAlert"
    // Long enough for the longest sound (missiles, ~10 s), so an FCM wake-up keeps the app alive until it ends.
    private const val MAX_PLAY_MS = 12_000L

    private var player: MediaPlayer? = null

    @RawRes
    private fun soundFor(kind: String): Int = when (kind) {
        "drones" -> R.raw.alert_drones
        "end" -> R.raw.alert_end
        // An alert of unknown type sounds like a missile alert: assume the worst.
        else -> R.raw.alert_missiles
    }

    /**
     * Plays the sound and waits for it to end (at most [MAX_PLAY_MS]). For FCM's background
     * thread: the app may be closed again as soon as the message handler returns.
     */
    fun playAndWait(context: Context, kind: String) {
        val done = CountDownLatch(1)
        if (start(context, kind) { done.countDown() }) {
            done.await(MAX_PLAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    private fun start(context: Context, kind: String, onDone: (() -> Unit)?): Boolean {
        // A newer alert replaces the one still sounding (the all-clear cuts the siren short).
        player?.release()
        player = null

        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            Log.w(TAG, "Alarm volume is 0: the alert is silent")
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = MediaPlayer.create(context.applicationContext, soundFor(kind), attributes, audio.generateAudioSessionId())
        if (created == null) {
            Log.w(TAG, "Could not load the $kind sound")
            return false
        }
        created.setOnCompletionListener { finished ->
            synchronized(this) {
                if (player === finished) player = null
            }
            finished.release()
            onDone?.invoke()
        }
        player = created
        created.start()
        return true
    }
}
