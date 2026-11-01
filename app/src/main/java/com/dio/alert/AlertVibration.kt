package com.dio.alert

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Vibrates an alert's pattern itself, as an alarm (felt with the ringer on silent), so the
 * vibration can be switched off in the app. The notification channels don't vibrate.
 */
object AlertVibration {
    /** An alert of unknown type vibrates like a missile alert: assume the worst. */
    private fun patternFor(kind: String): LongArray = when (kind) {
        // 2 short pulses, 3 times.
        "drones" -> pulses(2, 150, 100, 3, 600)
        // 1 long pulse, 2 times.
        "end" -> pulses(1, 1500, 0, 2, 600)
        // 3 medium pulses, 5 times.
        else -> pulses(3, 400, 150, 5, 600)
    }

    fun vibrate(context: Context, kind: String) {
        if (!Prefs(context).vibrationEnabled) {
            return
        }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) {
            return
        }
        val effect = VibrationEffect.createWaveform(patternFor(kind), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    /** Vibration pattern (ms: wait, vibrate, pause, vibrate, …) of [repeats] groups of [count] pulses. */
    private fun pulses(count: Int, pulseMs: Long, gapMs: Long, repeats: Int, groupGapMs: Long): LongArray {
        val pattern = mutableListOf(0L)
        repeat(repeats) { group ->
            if (group > 0) pattern += groupGapMs
            repeat(count) { pulse ->
                if (pulse > 0) pattern += gapMs
                pattern += pulseMs
            }
        }
        return pattern.toLongArray()
    }
}
