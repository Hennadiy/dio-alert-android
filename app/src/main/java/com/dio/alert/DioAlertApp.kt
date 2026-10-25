package com.dio.alert

import android.app.Application

class DioAlertApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Channels must exist before the first alert arrives, even if the app is never opened again.
        AlertNotifier.createChannels(this)
        UpdateWorker.schedule(this)
    }
}
