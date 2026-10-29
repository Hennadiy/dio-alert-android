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
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Shows air alert notifications. Android takes a notification's vibration and sound from
 * its channel, so every alert kind has a channel of its own; that is the whole reason this
 * is a native app rather than web push.
 *
 * Usually Android shows alerts itself from the FCM message (the server names the channel
 * and these string resources), so this only runs while the app is open, and for the local
 * tests; it shows them the same way. The channel ids and string names are part of the
 * server contract (AirAlertNotifier.cs in mind-portal-service): rename them together.
 *
 * A channel's sound and vibration are fixed once it is created on the phone. To change
 * them, bump the channel id (…_v2) here and on the server so a new channel is created.
 */
object AlertNotifier {
    // FCM displays with this tag and id 0; using the same means any alert, shown by FCM or
    // by the app, replaces the previous one (the all-clear replaces the alert it ends).
    private const val NOTIFICATION_TAG = "air-alert"
    private const val NOTIFICATION_ID = 0

    private enum class Channel(
        val id: String,
        @StringRes val nameRes: Int,
        @RawRes val soundRes: Int,
        val vibration: LongArray
    ) {
        // 2 short pulses, 3 times.
        DRONES("alert_drones_v2", R.string.channel_drones, R.raw.alert_drones, pulses(2, 150, 100, 3, 600)),
        // 3 medium pulses, 5 times.
        MISSILES("alert_missiles_v2", R.string.channel_missiles, R.raw.alert_missiles, pulses(3, 400, 150, 5, 600)),
        // 1 long pulse, 2 times.
        END("alert_end_v2", R.string.channel_end, R.raw.alert_end, pulses(1, 1500, 0, 2, 600))
    }

    // v1 channels pointed at their sounds by numeric resource id, which moves when a new
    // version adds resources, so they went silent after an update. Removed on start.
    private val OBSOLETE_CHANNELS = listOf("alert_drones_v1", "alert_missiles_v1", "alert_end_v1")

    /** An alert of unknown type uses the missile channel: assume the worst. */
    private fun channelFor(kind: String): Channel = when (kind) {
        "drones" -> Channel.DRONES
        "end" -> Channel.END
        else -> Channel.MISSILES
    }

    fun createChannels(context: Context) {
        // Alarm usage plays at alarm volume, so the sound is heard with the ringer on
        // silent or vibrate, and Do Not Disturb lets alarms through by default.
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        OBSOLETE_CHANNELS.forEach { manager.deleteNotificationChannel(it) }
        Channel.entries.forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel.id, context.getString(channel.nameRes), NotificationManager.IMPORTANCE_HIGH).apply {
                    // By name, not numeric id: the channel keeps this URI for good, and ids change between builds.
                    val sound = context.resources.getResourceEntryName(channel.soundRes)
                    setSound(Uri.parse("android.resource://${context.packageName}/raw/$sound"), audio)
                    enableVibration(true)
                    vibrationPattern = channel.vibration
                    enableLights(true)
                    lightColor = Color.RED
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    /** [minutes] is how long the alert lasted (all-clear only, when the server knows it). */
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

        // Same arguments as the server's title_loc_args: Ukrainian strings use the first, English the second.
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
