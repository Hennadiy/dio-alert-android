package com.dio.alert

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Checks GitHub for a newer release once a day and posts a quiet "update available" notification. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val update = try {
            Updater.findUpdate()
        } catch (e: Exception) {
            return Result.retry()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // One notification per release, not one a day.
        if (update != null && prefs.getString(KEY_NOTIFIED_VERSION, null) != update.version) {
            notify(applicationContext, update.version)
            prefs.edit().putString(KEY_NOTIFIED_VERSION, update.version).apply()
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "update-check"
        private const val CHANNEL_ID = "updates"
        private const val NOTIFICATION_ID = 100
        private const val PREFS = "dio_alert_updates"
        private const val KEY_NOTIFIED_VERSION = "notified_version"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, version: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            // Its own low-importance channel: an update must never sound like an alert.
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_LOW)
            )

            val open = PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_body, version))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
