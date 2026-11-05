package com.dio.alert

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Where tapping the alert notification goes. It may show over the lock screen, so the sound and
 * vibration stop at the tap itself, before any unlock; then it opens the app (which asks to
 * unlock, as usual) and closes. It has no UI of its own.
 */
class AlertTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AlertSound.stop()
        AlertVibration.cancel(this)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
