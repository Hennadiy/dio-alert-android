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
 * so the app always builds the notification itself, one channel per alert kind (so each can be
 * set apart, e.g. to override Do Not Disturb). The channels are silent and still: AlertSound and
 * AlertVibration play the sound and vibration, which the app's switches control.
 *
 * A channel's settings are fixed once it is created on the phone. To change them, bump the
 * channel ids (…_v5) and add the old ones to OBSOLETE_CHANNELS.
 */
object AlertNotifier {
    // FCM displays with this tag and id 0; using the same means any alert, shown by FCM or
    // by the app, replaces the previous one (the all-clear replaces the alert it ends).
    private const val NOTIFICATION_TAG = "air-alert"
    private const val NOTIFICATION_ID = 0

    private enum class Channel(val id: String, @StringRes val nameRes: Int) {
        DRONES("alert_drones_v4", R.string.channel_drones),
        MISSILES("alert_missiles_v4", R.string.channel_missiles),
        END("alert_end_v4", R.string.channel_end)
    }

    // v1 pointed at its sounds by numeric resource id (silent after updates); v2 played the sound
    // through the channel (silent on some phones); v3 vibrated through the channel, which the
    // app's vibration switch couldn't turn off. Removed on start.
    private val OBSOLETE_CHANNELS = listOf(
        "alert_drones_v1", "alert_missiles_v1", "alert_end_v1",
        "alert_drones_v2", "alert_missiles_v2", "alert_end_v2",
        "alert_drones_v3", "alert_missiles_v3", "alert_end_v3"
    )

    /** Names of the alert kinds whose channel the user switched off (their alerts don't show or vibrate). */
    fun blockedChannelNames(context: Context): List<String> {
        val manager = context.getSystemService(NotificationManager::class.java)
        return Channel.entries
            .filter { manager.getNotificationChannel(it.id)?.importance == NotificationManager.IMPORTANCE_NONE }
            .map { context.getString(it.nameRes) }
    }

    /**
     * Alert kinds (channel id to name) still switched on whose channel can't break through Do Not
     * Disturb. Only the user can turn "Override Do Not Disturb" on, in the channel's settings.
     */
    fun channelsNotOverridingDnd(context: Context): List<Pair<String, String>> {
        val manager = context.getSystemService(NotificationManager::class.java)
        return Channel.entries
            .filter { channel ->
                val created = manager.getNotificationChannel(channel.id)
                created != null && created.importance != NotificationManager.IMPORTANCE_NONE && !created.canBypassDnd()
            }
            .map { it.id to context.getString(it.nameRes) }
    }

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
                    // Neither sound nor vibration: AlertSound and AlertVibration do both, so the
                    // app's switches control them and nothing plays twice.
                    setSound(null, null)
                    enableVibration(false)
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
}
