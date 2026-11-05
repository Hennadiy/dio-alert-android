package com.dio.alert

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var regionCard: View
    private lateinit var setupCard: View
    private lateinit var alertSettingsCard: View
    private lateinit var regionButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var accountChip: View
    private lateinit var accountInitial: TextView
    private lateinit var accountEmail: TextView
    private lateinit var disconnectButton: MaterialButton
    private lateinit var signInGroup: View
    private lateinit var signInButton: MaterialButton
    private lateinit var modeButton: MaterialButton
    private lateinit var forgotButton: MaterialButton
    private lateinit var registerFields: View
    private lateinit var registerHint: View
    private lateinit var firstNameInput: TextInputEditText
    private lateinit var lastNameInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    /** The form shows registration instead of sign-in. */
    private var isRegistering = false
    private lateinit var issuesList: LinearLayout
    /** Asked for notifications once while this screen is open: a second refusal means "go to settings". */
    private var askedForNotifications = false

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        regionCard = findViewById(R.id.regionCard)
        setupCard = findViewById(R.id.setupCard)
        alertSettingsCard = findViewById(R.id.alertSettingsCard)
        setUpAlertSettings()
        regionButton = findViewById(R.id.regionButton)
        statusText = findViewById(R.id.statusText)
        connectionText = findViewById(R.id.connectionText)
        accountChip = findViewById(R.id.accountChip)
        accountInitial = findViewById(R.id.accountInitial)
        accountEmail = findViewById(R.id.accountEmail)
        disconnectButton = findViewById(R.id.disconnectButton)
        signInGroup = findViewById(R.id.signInGroup)
        signInButton = findViewById(R.id.signInButton)
        modeButton = findViewById(R.id.modeButton)
        forgotButton = findViewById(R.id.forgotButton)
        registerFields = findViewById(R.id.registerFields)
        registerHint = findViewById(R.id.registerHint)
        firstNameInput = findViewById(R.id.firstNameInput)
        lastNameInput = findViewById(R.id.lastNameInput)
        phoneInput = findViewById(R.id.phoneInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        issuesList = findViewById(R.id.issuesList)
        findViewById<MaterialButton>(R.id.appSettingsButton).setOnClickListener { openAppSettings() }
        findViewById<MaterialButton>(R.id.testButton).setOnClickListener { showTestAlerts() }
        findViewById<MaterialButton>(R.id.notificationSettingsButton).setOnClickListener { openNotificationSettings() }
        findViewById<TextView>(R.id.versionText).text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        findViewById<MaterialButton>(R.id.languageButton).setOnClickListener { pickLanguage() }

        regionButton.setOnClickListener { pickRegion() }
        signInButton.setOnClickListener { if (isRegistering) register() else signIn() }
        modeButton.setOnClickListener { setRegistering(!isRegistering) }
        forgotButton.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.MIND_URL + "forgot-password"))) }
        disconnectButton.setOnClickListener { disconnect() }

        // Android 13+ shows nothing until the user allows notifications; ask as soon as alerts can arrive.
        if (prefs.isConnected && !hasNotificationPermission()) {
            requestNotifications()
        }
    }

    override fun onResume() {
        super.onResume()
        // Opening the app while an alert sounds silences it: the user has seen it.
        AlertSound.stop()
        AlertVibration.cancel(this)
        updateRegion()
        updateConnection() // also refreshes the setup checks
        loadStatus()
        refreshRegistration()
    }

    private fun setRegistering(registering: Boolean) {
        isRegistering = registering
        registerFields.visibility = if (registering) View.VISIBLE else View.GONE
        registerHint.visibility = if (registering) View.VISIBLE else View.GONE
        forgotButton.visibility = if (registering) View.GONE else View.VISIBLE
        signInButton.setText(if (registering) R.string.register_submit else R.string.sign_in)
        modeButton.setText(if (registering) R.string.have_account else R.string.register)
    }

    private fun text(input: TextInputEditText) = input.text?.toString()?.trim().orEmpty()

    /** Marks empty fields; true when all are filled. */
    private fun requireFilled(vararg inputs: TextInputEditText): Boolean {
        var ok = true
        inputs.forEach { input ->
            if (text(input).isEmpty()) {
                input.error = getString(R.string.field_required)
                ok = false
            }
        }
        return ok
    }

    /** Email + password (and an invisible reCAPTCHA) → a device key for this phone, made by the server. */
    private fun signIn() {
        if (!requireFilled(emailInput, passwordInput)) return
        val email = text(emailInput)
        val password = passwordInput.text?.toString().orEmpty()

        signInButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val captcha = AppCaptcha.loginToken(application)
                val (key, signedInAs) = withContext(Dispatchers.IO) {
                    ApiClient.appSignIn(email, password, captcha, "Dio Alert · ${Prefs.deviceName}")
                }
                val token = FirebaseMessaging.getInstance().token.await()
                withContext(Dispatchers.IO) { ApiClient.registerDevice(key, token, Prefs.deviceName, prefs.region.uid) }
                prefs.deviceKey = key
                prefs.registeredToken = token
                prefs.userEmail = signedInAs
                passwordInput.setText("")
                toast(R.string.connected)
                if (!hasNotificationPermission()) requestNotifications()
            } catch (e: Exception) {
                toast(getString(R.string.sign_in_failed, errorText(e)))
            } finally {
                signInButton.isEnabled = true
                updateConnection()
            }
        }
    }

    private fun register() {
        if (!requireFilled(firstNameInput, lastNameInput, phoneInput, emailInput, passwordInput)) return
        val password = passwordInput.text?.toString().orEmpty()
        if (password.length < 8) {
            passwordInput.error = getString(R.string.password_too_short)
            return
        }

        signInButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val captcha = AppCaptcha.signupToken(application)
                withContext(Dispatchers.IO) {
                    ApiClient.appRegister(text(firstNameInput), text(lastNameInput), text(emailInput), text(phoneInput), password, captcha)
                }
                passwordInput.setText("")
                setRegistering(false)
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.registered_title)
                    .setMessage(R.string.registered_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (e: Exception) {
                toast(getString(R.string.register_failed, errorText(e)))
            } finally {
                signInButton.isEnabled = true
            }
        }
    }

    /** The server's message where there is one; plain words for the usual failures. */
    private fun errorText(e: Exception): String = when (e) {
        is AppCaptcha.NotConfiguredException -> getString(R.string.captcha_not_configured)
        is ApiClient.ApiException -> e.message ?: "HTTP ${e.code}"
        is java.io.IOException -> getString(R.string.no_connection)
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun disconnect() {
        val key = prefs.deviceKey
        val token = prefs.registeredToken
        prefs.clearConnection()
        updateConnection()
        if (key != null) {
            lifecycleScope.launch {
                // Best effort: if it fails, the phone stays in Mind's list until it is removed there.
                runCatching { withContext(Dispatchers.IO) { ApiClient.unregisterDevice(key, token, revokeKey = true) } }
            }
        }
    }

    /** Re-registers on every start: picks up a rotated token and keeps "last seen" fresh in Mind. */
    private fun refreshRegistration() {
        val key = prefs.deviceKey ?: return
        lifecycleScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                withContext(Dispatchers.IO) { ApiClient.registerDevice(key, token, Prefs.deviceName, prefs.region.uid) }
                prefs.registeredToken = token
            } catch (e: ApiClient.ApiException) {
                if (e.code == 401) {
                    // The key was revoked in Mind.
                    prefs.clearConnection()
                    updateConnection()
                    toast(R.string.key_revoked)
                }
            } catch (e: Exception) {
                // Offline: try again next time.
            }
        }
    }

    /** Sound and vibration switches (on by default) and the sound's loudness within the alarm volume. */
    private fun setUpAlertSettings() {
        val soundSwitch = findViewById<MaterialSwitch>(R.id.soundSwitch)
        val vibrationSwitch = findViewById<MaterialSwitch>(R.id.vibrationSwitch)
        val volumeSlider = findViewById<Slider>(R.id.volumeSlider)
        val volumeLabel = findViewById<TextView>(R.id.volumeLabel)

        val soundChoices = findViewById<LinearLayout>(R.id.soundChoices)
        val soundButtons = AlertSounds.KINDS.associateWith { kind ->
            MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setIconResource(R.drawable.ic_expand_more)
                iconGravity = MaterialButton.ICON_GRAVITY_END
                setOnClickListener { pickSound(kind) { updateSoundButton(this, kind) } }
                updateSoundButton(this, kind)
                soundChoices.addView(this)
            }
        }

        fun showVolume() {
            volumeLabel.text = getString(R.string.sound_volume_value, volumeSlider.value.toInt())
            volumeSlider.isEnabled = soundSwitch.isChecked
            volumeLabel.isEnabled = soundSwitch.isChecked
            soundButtons.values.forEach { it.isEnabled = soundSwitch.isChecked }
        }

        soundSwitch.isChecked = prefs.soundEnabled
        vibrationSwitch.isChecked = prefs.vibrationEnabled
        volumeSlider.value = (prefs.soundVolume * 100).toInt().coerceIn(0, 100) / 10 * 10f
        showVolume()

        soundSwitch.setOnCheckedChangeListener { _, checked ->
            confirmSwitchOff(soundSwitch, checked, R.string.sound_off_confirm) { on ->
                prefs.soundEnabled = on
                showVolume()
                updateSetup()
            }
        }
        vibrationSwitch.setOnCheckedChangeListener { _, checked ->
            confirmSwitchOff(vibrationSwitch, checked, R.string.vibration_off_confirm) { on -> prefs.vibrationEnabled = on }
        }
        volumeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.soundVolume = value / 100f
                showVolume()
            }
        }
        // Hear it at the new loudness when the thumb is let go.
        volumeSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                if (soundSwitch.isChecked) AlertSound.preview(this@MainActivity, "drones")
            }
        })
    }

    /**
     * Turning sound or vibration off silences real alerts too, so it asks first; Cancel flips the
     * switch back. Turning it on needs no question.
     */
    private fun confirmSwitchOff(switch: MaterialSwitch, checked: Boolean, @StringRes messageRes: Int, apply: (Boolean) -> Unit) {
        if (checked) {
            apply(true)
            return
        }
        var confirmed = false
        MaterialAlertDialogBuilder(this)
            .setMessage(messageRes)
            .setPositiveButton(R.string.turn_off) { _, _ ->
                confirmed = true
                apply(false)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                if (!confirmed) {
                    // Back on without asking again: the listener sees "checked" and just applies it.
                    switch.isChecked = true
                }
            }
            .show()
    }

    private fun kindName(kind: String): String = getString(
        when (kind) {
            "drones" -> R.string.channel_drones
            "end" -> R.string.channel_end
            else -> R.string.channel_missiles
        }
    )

    private fun updateSoundButton(button: MaterialButton, kind: String) {
        button.text = getString(R.string.sound_for_kind, kindName(kind), getString(prefs.sound(kind).nameRes))
    }

    /** Lists the sounds for one alert kind; tapping one plays it, OK keeps it. */
    private fun pickSound(kind: String, onPicked: () -> Unit) {
        val sounds = AlertSounds.entries
        var chosen = prefs.sound(kind)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.sound_pick_title, kindName(kind)))
            .setSingleChoiceItems(sounds.map { getString(it.nameRes) }.toTypedArray(), sounds.indexOf(chosen)) { _, which ->
                chosen = sounds[which]
                AlertSound.preview(this, chosen)
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.setSound(kind, chosen)
                onPicked()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { AlertSound.stop() }
            .show()
    }

    /** Plays a test alert of the chosen kind on this phone: the notification, vibration and sound a real one gets. */
    private fun showTestAlerts() {
        val kinds = listOf("missiles", "drones", "end")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.test_alerts)
            .setItems(kinds.map { kindName(it) }.toTypedArray()) { _, which ->
                val kind = kinds[which]
                AlertNotifier.show(this, kind, prefs.region, if (kind == "end") 42 else null, isTest = true)
                AlertVibration.vibrate(this, kind)
                AlertSound.play(this, kind)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateRegion() {
        regionButton.text = prefs.region.displayName(this)
    }

    /** Lists Mind's regions (or the last list seen, offline); picking one re-registers the phone for it. */
    private fun pickRegion() {
        regionButton.isEnabled = false
        lifecycleScope.launch {
            val regions = try {
                withContext(Dispatchers.IO) { ApiClient.getRegions() }.also { prefs.regions = it }
            } catch (e: Exception) {
                prefs.regions
            } finally {
                regionButton.isEnabled = true
            }
            if (regions.isEmpty()) {
                toast(R.string.regions_unavailable)
                return@launch
            }

            val current = regions.indexOfFirst { it.uid == prefs.region.uid }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.region_pick_title)
                .setSingleChoiceItems(regions.map { it.displayName(this@MainActivity) }.toTypedArray(), current) { dialog, which ->
                    dialog.dismiss()
                    changeRegion(regions[which])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun changeRegion(region: Region) {
        if (region.uid == prefs.region.uid) {
            return
        }
        val previous = prefs.region
        prefs.region = region
        updateRegion()
        showStatus(null)
        loadStatus()

        val key = prefs.deviceKey ?: return
        lifecycleScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                withContext(Dispatchers.IO) { ApiClient.registerDevice(key, token, Prefs.deviceName, region.uid) }
                prefs.registeredToken = token
                toast(getString(R.string.region_changed, region.displayName(this@MainActivity)))
            } catch (e: Exception) {
                // The server still sends the old region's alerts: say so and show that region again.
                prefs.region = previous
                updateRegion()
                loadStatus()
                toast(getString(R.string.request_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun loadStatus() {
        val regionUid = prefs.region.uid
        lifecycleScope.launch {
            val status = try {
                withContext(Dispatchers.IO) { ApiClient.getStatus(regionUid) }
            } catch (e: Exception) {
                null
            }
            showStatus(status)
        }
    }

    /** The region's state as a pill coloured by kind: yellow drones, red missiles, orange other alerts, green calm, grey unknown. */
    private fun showStatus(status: AlertStatus?) {
        val (textRes, bg, fg) = when {
            status == null || !status.isConfigured || status.isActive == null ->
                Triple(R.string.status_unknown, R.color.status_unknown_bg, R.color.status_unknown_fg)
            !status.isActive -> Triple(R.string.status_calm, R.color.status_calm_bg, R.color.status_calm_fg)
            status.kind == "drones" -> Triple(R.string.status_drones, R.color.status_drones_bg, R.color.status_drones_fg)
            status.kind == "missiles" -> Triple(R.string.status_missiles, R.color.status_missiles_bg, R.color.status_missiles_fg)
            else -> Triple(R.string.status_general, R.color.status_general_bg, R.color.status_general_fg)
        }
        // During an alert: when it started (when Mind saw it begin).
        val since = status?.since?.takeIf { status.isActive == true }?.let { formatTime(it) }
        statusText.text = if (since != null) getString(R.string.status_since, getString(textRes), since) else getString(textRes)
        statusText.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
        statusText.setTextColor(ContextCompat.getColor(this, fg))
    }

    private fun formatTime(iso: String): String? = runCatching {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
    }.getOrNull()

    private fun loadOtherDevices() {
        val key = prefs.deviceKey ?: return
        lifecycleScope.launch {
            val others = runCatching { withContext(Dispatchers.IO) { ApiClient.otherDevices(key) } }.getOrNull()
            if (!prefs.isConnected || others.isNullOrEmpty()) return@launch
            connectionText.text = getString(R.string.also_other_devices, others.joinToString(", "))
            connectionText.setTextColor(statusLabelColor())
            connectionText.visibility = View.VISIBLE
        }
    }

    private fun statusLabelColor(): Int {
        val value = android.util.TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, value, true)
        return value.data
    }

    private fun updateConnection() {
        val connected = prefs.isConnected
        val email = prefs.userEmail
        accountChip.visibility = if (connected && email != null) View.VISIBLE else View.GONE
        if (email != null) {
            accountEmail.text = email
            accountInitial.text = email.first().uppercase()
        }
        // Signed in, the line only speaks up when alerts also go to the account's other phones.
        if (connected) {
            connectionText.visibility = View.GONE
            loadOtherDevices()
        } else {
            connectionText.visibility = View.VISIBLE
            connectionText.text = getString(R.string.connection_off)
            connectionText.setTextColor(ContextCompat.getColor(this, R.color.danger))
        }
        signInGroup.visibility = if (connected) View.GONE else View.VISIBLE
        disconnectButton.visibility = if (connected) View.VISIBLE else View.GONE
        // Signed out, the phone gets no alerts: only signing in is offered.
        regionCard.visibility = if (connected) View.VISIBLE else View.GONE
        alertSettingsCard.visibility = if (connected) View.VISIBLE else View.GONE
        updateSetup()
    }

    /** Something on the phone that can stop alerts from arriving or sounding, and how to fix it. */
    private class SetupIssue(val text: CharSequence, val buttonRes: Int, val fix: () -> Unit)

    /**
     * Checks everything on the phone that can stop an alert from arriving, showing or sounding,
     * and lists only what needs fixing; the card hides when nothing does. Runs on every resume,
     * so coming back from Settings updates it.
     */
    private fun updateSetup() {
        val issues = if (prefs.isConnected) setupIssues() else emptyList()
        issuesList.removeAllViews()
        issues.forEach { issue ->
            val row = layoutInflater.inflate(R.layout.item_setup_issue, issuesList, false)
            row.findViewById<TextView>(R.id.issueText).text = issue.text
            row.findViewById<MaterialButton>(R.id.issueButton).apply {
                setText(issue.buttonRes)
                setOnClickListener { issue.fix() }
            }
            issuesList.addView(row)
        }
        setupCard.visibility = if (issues.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setupIssues(): List<SetupIssue> {
        val issues = mutableListOf<SetupIssue>()

        // Notifications: the Android 13+ permission, the app-wide switch, and each alert kind's channel.
        if (!hasNotificationPermission()) {
            issues += SetupIssue(getString(R.string.issue_notifications), R.string.allow_notifications) {
                if (askedForNotifications) openNotificationSettings() else requestNotifications()
            }
        } else if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            issues += SetupIssue(getString(R.string.issue_notifications), R.string.allow_notifications) { openNotificationSettings() }
        } else {
            val blocked = AlertNotifier.blockedChannelNames(this)
            if (blocked.isNotEmpty()) {
                issues += SetupIssue(getString(R.string.issue_channels, blocked.joinToString(", ")), R.string.notification_settings) {
                    openNotificationSettings()
                }
            }
        }

        // Battery optimisation can hold an alert back until the phone wakes up.
        val power = getSystemService(PowerManager::class.java)
        if (!power.isIgnoringBatteryOptimizations(packageName)) {
            issues += SetupIssue(getString(batteryHintRes()), R.string.battery_exemption) { requestBatteryExemption() }
        }

        // Android rations high-priority messages for apps it rates rarely used (standby buckets,
        // Android 9+): they arrive late. Exempting from battery optimisation moves the app out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            getSystemService(UsageStatsManager::class.java).appStandbyBucket >= UsageStatsManager.STANDBY_BUCKET_RARE
        ) {
            issues += SetupIssue(getString(R.string.issue_standby), R.string.app_settings) { openAppSettings() }
        }

        // "Restricted" background use (Android 9+) drops the messages that wake the app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            getSystemService(ActivityManager::class.java).isBackgroundRestricted
        ) {
            issues += SetupIssue(getString(R.string.issue_background), R.string.app_settings) { openAppSettings() }
        }

        // Data Saver cuts the app off the network in the background.
        val connectivity = getSystemService(ConnectivityManager::class.java)
        if (connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
            issues += SetupIssue(getString(R.string.issue_data_saver), R.string.allow_data) {
                startActivity(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
        }

        // The sound plays on the alarm stream: at zero alarm volume alerts are silent.
        val audio = getSystemService(AudioManager::class.java)
        if (prefs.soundEnabled && audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
            issues += SetupIssue(getString(R.string.issue_alarm_volume), R.string.turn_up_alarm_volume) {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audio.setStreamVolume(AudioManager.STREAM_ALARM, maxOf(1, max * 7 / 10), AudioManager.FLAG_SHOW_UI)
                updateSetup()
            }
        }

        // "Override Do Not Disturb" per alert kind: without it Do Not Disturb hides that kind's
        // notification. One entry per kind, each opening its own channel's settings.
        if (hasNotificationPermission() && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            AlertNotifier.channelsNotOverridingDnd(this).forEach { (channelId, name) ->
                issues += SetupIssue(getString(R.string.issue_dnd_override, name), R.string.dnd_override_button) {
                    openChannelSettings(channelId)
                }
            }
        }

        // Do Not Disturb lets alarms through unless it is "total silence" or alarms are turned off in it.
        if (dndBlocksAlarms()) {
            issues += SetupIssue(getString(R.string.issue_dnd), R.string.dnd_settings) {
                startActivity(Intent("android.settings.ZEN_MODE_SETTINGS"))
            }
        }
        // Adaptive battery learns which apps to hold back; an alert app used rarely is one.
        if (Settings.Global.getInt(contentResolver, "adaptive_battery_management_enabled", 0) == 1) {
            issues += SetupIssue(getString(R.string.issue_adaptive_battery), R.string.battery_settings) { openBatterySettings() }
        }

        // Maker settings can't be read, so their result is checked instead: they show until an
        // alert arrives on time with the screen off, and come back if one arrives late.
        val delivery = prefs.lastDelivery
        val provenOnTime = delivery != null && delivery.screenOff && delivery.delayMs <= ON_TIME_MS
        if (!provenOnTime) {
            val steps = makerSteps()
            if (steps.isNotEmpty()) {
                val first = steps.first()
                issues += SetupIssue(getString(R.string.maker_steps_hint) + "\n\n" + first.text, first.buttonRes, first.fix)
                issues += steps.drop(1)
            }
        }
        return issues
    }

    /** Settings some makers add on top of Android that hold alerts back, which no API can read. */
    private fun makerSteps(): List<SetupIssue> = when (Build.MANUFACTURER.lowercase()) {
        "oneplus", "oppo", "realme" -> listOf(
            SetupIssue(getString(R.string.step_sleep_standby), R.string.battery_settings) { openBatterySettings() },
            SetupIssue(getString(R.string.step_lock_recents), R.string.open_recents) { openRecents() }
        )
        "xiaomi", "redmi", "poco" -> listOf(
            SetupIssue(getString(R.string.step_autostart), R.string.app_settings) { openAppSettings() },
            SetupIssue(getString(R.string.step_lock_recents), R.string.open_recents) { openRecents() }
        )
        "samsung" -> listOf(
            SetupIssue(getString(R.string.step_never_sleeping), R.string.battery_settings) { openBatterySettings() }
        )
        else -> emptyList()
    }

    private fun openBatterySettings() {
        val battery = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        startActivity(if (battery.resolveActivity(packageManager) != null) battery else Intent(Settings.ACTION_SETTINGS))
    }

    // There is no intent for the recent apps screen; going home leaves the user one swipe away.
    private fun openRecents() {
        toast(R.string.recents_hint)
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun dndBlocksAlarms(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        return when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> true
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                manager.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0
            else -> false
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Where "unrestricted" is on this phone's settings page: it moved between Android versions, and Samsung and Xiaomi have their own. */
    private fun batteryHintRes(): Int {
        val maker = Build.MANUFACTURER.lowercase()
        return when {
            maker == "samsung" -> R.string.battery_hint_samsung
            maker == "oneplus" -> R.string.battery_hint_oneplus
            maker in setOf("xiaomi", "redmi", "poco") -> R.string.battery_hint_xiaomi
            Build.VERSION.SDK_INT >= 34 -> R.string.battery_hint_14
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> R.string.battery_hint_12
            else -> R.string.battery_hint_8
        }
    }

    // An alert app is exactly the case this exemption exists for: a delayed alert is useless.
    // Google Play restricts the direct one-tap exemption request, and the optimisation list
    // opens filtered to apps already exempt, so this opens Dio Alert's own settings page,
    // where Battery → Unrestricted is on every maker's Android.
    private fun requestBatteryExemption() = openAppSettings()

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun openChannelSettings(channelId: String) {
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        )
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    /**
     * As on the phone (Ukrainian on a Ukrainian phone, English otherwise), or one of the
     * two for this app only. AppCompat recreates the activity in the new language.
     */
    private fun pickLanguage() {
        val tags = listOf("", "uk", "en")
        val current = tags.indexOf(AppCompatDelegate.getApplicationLocales().toLanguageTags()).coerceAtLeast(0)
        val names = arrayOf(getString(R.string.language_system), "Українська", "English")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(names, current) { dialog, which ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(resId: Int) = toast(getString(resId))

    companion object {
        /** An alert this quick with the screen off proves the phone isn't holding alerts back. */
        const val ON_TIME_MS = 30_000L
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
