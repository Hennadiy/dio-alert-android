package com.dio.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The alert notification was swiped away or cleared: stop its sound and vibration. */
class StopAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertSound.stop()
        AlertVibration.cancel(context)
    }
}
