package com.dio.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.RawRes
import java.util.Timer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer

/**
 * Plays an alert's sound itself, on the alarm stream: the alarm volume, heard with the ringer on
 * silent or vibrate, and let through Do Not Disturb by default. Notification-channel sounds were
 * silent on some phones (Motorola), so the channels carry neither sound nor vibration.
 *
 * Alerts fade in over [FADE_IN_MS] rather than starting at full volume, so they wake without
 * startling. Dismissing the notification stops the sound ([stop], via StopAlertReceiver).
 */
object AlertSound {
    private const val TAG = "DioAlert"
    // Long enough for the longest sound (missiles, ~10 s), so an FCM wake-up keeps the app alive until it ends.
    private const val MAX_PLAY_MS = 12_000L
    private const val FADE_IN_MS = 3_000L
    private const val FADE_STEP_MS = 100L
    // Where the fade starts, as a share of the full volume.
    private const val FADE_FROM = 0.1f

    private var player: MediaPlayer? = null
    private var fade: Timer? = null
    // Lets a waiting playAndWait return as soon as the sound is stopped.
    private var onDone: (() -> Unit)? = null

    /** Plays a kind's picked sound without waiting, ignoring the switch (the settings card's "Try"). */
    fun preview(context: Context, kind: String) {
        start(context, Prefs(context).sound(kind).rawRes, fadeIn = false, null)
    }

    /** Plays one sound without waiting (choosing a sound in the settings card). */
    fun preview(context: Context, sound: AlertSounds) {
        start(context, sound.rawRes, fadeIn = false, null)
    }

    /** Plays a kind's alert sound as a real alert would (switch, fade-in), without waiting: the test dialog. */
    fun play(context: Context, kind: String) {
        if (Prefs(context).soundEnabled) {
            start(context, Prefs(context).sound(kind).rawRes, fadeIn = true, null)
        }
    }

    /**
     * Plays the sound and waits for it to end (at most [MAX_PLAY_MS]). For FCM's background
     * thread: the app may be closed again as soon as the message handler returns.
     */
    fun playAndWait(context: Context, kind: String) {
        if (!Prefs(context).soundEnabled) {
            return
        }
        val done = CountDownLatch(1)
        if (start(context, Prefs(context).sound(kind).rawRes, fadeIn = true) { done.countDown() }) {
            done.await(MAX_PLAY_MS, TimeUnit.MILLISECONDS)
        }
    }

    /** Stops whatever is playing: the notification was dismissed, or the sound chooser closed. */
    @Synchronized
    fun stop() {
        fade?.cancel()
        fade = null
        player?.release()
        player = null
        onDone?.invoke()
        onDone = null
    }

    @Synchronized
    private fun start(context: Context, @RawRes sound: Int, fadeIn: Boolean, done: (() -> Unit)?): Boolean {
        // A newer alert replaces the one still sounding (the all-clear cuts the siren short).
        stop()

        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            Log.w(TAG, "Alarm volume is 0: the alert is silent")
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = MediaPlayer.create(context.applicationContext, sound, attributes, audio.generateAudioSessionId())
        if (created == null) {
            Log.w(TAG, "Could not load sound $sound")
            return false
        }
        created.setOnCompletionListener { finished ->
            synchronized(this) {
                if (player === finished) {
                    fade?.cancel()
                    fade = null
                    player = null
                    onDone = null
                }
            }
            finished.release()
            done?.invoke()
        }
        // Within the alarm volume: the app's slider can make it quieter, never louder.
        val volume = Prefs(context).soundVolume
        player = created
        onDone = done
        if (fadeIn) {
            created.setVolume(volume * FADE_FROM, volume * FADE_FROM)
            val started = System.currentTimeMillis()
            fade = fixedRateTimer("alert-fade", daemon = true, period = FADE_STEP_MS) {
                val progress = ((System.currentTimeMillis() - started).toFloat() / FADE_IN_MS).coerceAtMost(1f)
                val level = volume * (FADE_FROM + (1 - FADE_FROM) * progress)
                synchronized(this@AlertSound) {
                    if (player !== created) {
                        cancel()
                        return@fixedRateTimer
                    }
                    created.setVolume(level, level)
                }
                if (progress >= 1f) cancel()
            }
        } else {
            created.setVolume(volume, volume)
        }
        created.start()
        return true
    }
}
