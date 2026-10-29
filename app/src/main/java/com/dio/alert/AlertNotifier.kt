package com.dio.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Shows air alert notifications. Alerts arrive as data-only FCM messages (AlertMessagingService),
 * so the app always builds the notification itself. Android takes a notification's vibration from
 * its channel, so every alert kind has a channel of its own; the sound is played by AlertSound on
 * the alarm stream, because channel sounds were silent on some phones.
 *
 * A channel's settings are fixed once it is created on the phone. To change the vibration, bump
 * the channel id (…_v4) and add the old one to OBSOLETE_CHANNELS.
 */
object AlertNotifier {
    // FCM displays with this tag and id 0; using the same means any alert, shown by FCM or
    // by the app, replaces the previous one (the all-clear replaces the alert it ends).
    private const val NOTIFICATION_TAG = "air-alert"
    private const val NOTIFICATION_ID = 0

    private enum class Channel(
        val id: String,
        @StringRes val nameRes: Int,
        val vibration: LongArray
    ) {
        // 2 short pulses, 3 times.
        DRONES("alert_drones_v3", R.string.channel_drones, pulses(2, 150, 100, 3, 600)),
        // 3 medium pulses, 5 times.
        MISSILES("alert_missiles_v3", R.string.channel_missiles, pulses(3, 400, 150, 5, 600)),
        // 1 long pulse, 2 times.
        END("alert_end_v3", R.string.channel_end, pulses(1, 1500, 0, 2, 600))
    }

    // v1 pointed at its sounds by numeric resource id (silent after updates); v2 played the sound
    // through the channel (silent on some phones). Removed on start.
    private val OBSOLETE_CHANNELS = listOf(
        "alert_drones_v1", "alert_missiles_v1", "alert_end_v1",
        "alert_drones_v2", "alert_missiles_v2", "alert_end_v2"
    )

    /** An alert of unknown type uses the missile channel: assume the worst. */
    private fun channelFor(kind: String): Channel = when (kind) {
        "drones" -> Channel.DRONES
        "end" -> Channel.END
        else -> Channel.MISSILES
    }

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        OBSOLETE_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        Channel.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel.id, context.getString(channel.nameRes), NotificationManager.IMPORTANCE_HIGH).apply {
                    // No channel sound: AlertSound plays it, so it isn't heard twice.
                    setSound(null, null)
                    enableVibration(true)
                    vibrationPattern = channel.vibration
                    enableLights(true)
                    lightColor = Color.RED
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    /** [minutes] is how long the alert lasted (all-clear only, when the server knows it). The sound is AlertSound's job. */
    fun show(context: Context, kind: String, region: Region, minutes: Int?, isTest: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val (titleRes, bodyRes) = when (kind) {
            "drones" -> R.string.alert_drones_title to R.string.alert_drones_body
            "missiles" -> R.string.alert_missiles_title to R.string.alert_missiles_body
            "end" -> R.string.alert_end_title to R.string.alert_end_body
            else -> R.string.alert_general_title to R.string.alert_general_body
        }

        // Ukrainian strings use the first argument, English ones the second.
        val title = context.getString(titleRes, region.name, region.nameEn)
        val body = when {
            isTest -> context.getString(R.string.alert_test_body)
            kind == "end" && minutes != null -> formatDuration(context, minutes)
            else -> context.getString(bodyRes)
        }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelFor(kind).id)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setColor(ContextCompat.getColor(context, if (kind == "end") R.color.calm else R.color.danger))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
    }

    // String arguments, because FCM fills the same resources with its string body_loc_args.
    private fun formatDuration(context: Context, minutes: Int): String =
        if (minutes >= 60) context.getString(R.string.alert_end_duration_hours, (minutes / 60).toString(), (minutes % 60).toString())
        else context.getString(R.string.alert_end_duration_minutes, minutes.toString())

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
