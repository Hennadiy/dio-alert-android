package com.dio.alert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var regionButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var connectionText: TextView
    private lateinit var keyInput: TextInputEditText
    private lateinit var keyGroup: View
    private lateinit var connectButton: MaterialButton
    private lateinit var disconnectButton: MaterialButton
    private lateinit var signInGroup: View
    private lateinit var signInButton: MaterialButton
    private lateinit var notificationsButton: MaterialButton
    private lateinit var batteryButton: MaterialButton
    private lateinit var batteryHint: TextView

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateSetupButtons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        regionCard = findViewById(R.id.regionCard)
        setupCard = findViewById(R.id.setupCard)
        regionButton = findViewById(R.id.regionButton)
        statusText = findViewById(R.id.statusText)
        connectionText = findViewById(R.id.connectionText)
        keyInput = findViewById(R.id.keyInput)
        keyGroup = findViewById(R.id.keyGroup)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        signInGroup = findViewById(R.id.signInGroup)
        signInButton = findViewById(R.id.signInButton)
        notificationsButton = findViewById(R.id.notificationsButton)
        batteryButton = findViewById(R.id.batteryButton)
        batteryHint = findViewById(R.id.batteryHint)
        batteryHint.setText(batteryHintRes())
        findViewById<TextView>(R.id.versionText).text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        findViewById<MaterialButton>(R.id.languageButton).setOnClickListener { pickLanguage() }
        findViewById<TextView>(R.id.sourceText).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ALERTS_SOURCE_URL)))
        }

        regionButton.setOnClickListener { pickRegion() }
        signInButton.setOnClickListener { MindAuth.startSignIn(this) }
        findViewById<MaterialButton>(R.id.registerButton).setOnClickListener { MindAuth.openRegistration(this) }
        findViewById<MaterialButton>(R.id.useKeyButton).setOnClickListener {
            keyGroup.visibility = if (keyGroup.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        connectButton.setOnClickListener { connect() }
        disconnectButton.setOnClickListener { disconnect() }
        notificationsButton.setOnClickListener { requestNotifications() }
        batteryButton.setOnClickListener { requestBatteryExemption() }
        findViewById<MaterialButton>(R.id.channelSettingsButton).setOnClickListener { openNotificationSettings() }
        findViewById<MaterialButton>(R.id.guideButton).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SETUP_GUIDE_URL)))
        }

        // Android 13+ shows nothing until the user allows notifications; ask as soon as alerts can arrive.
        if (prefs.isConnected && !hasNotificationPermission()) {
            requestNotifications()
        }
        handleSignInRedirect(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSignInRedirect(intent)
    }

    /** The login page came back with an authorization code: finish signing in. */
    private fun handleSignInRedirect(intent: Intent?) {
        val redirect = intent?.data
        if (!MindAuth.isRedirect(redirect) || redirect == null) {
            return
        }
        // Handled once, even if the activity is recreated with the same intent.
        intent?.data = null

        signInButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val (key, email) = withContext(Dispatchers.IO) { MindAuth.completeSignIn(this@MainActivity, redirect) }
                val token = FirebaseMessaging.getInstance().token.await()
                withContext(Dispatchers.IO) { ApiClient.registerDevice(key, token, Prefs.deviceName, prefs.region.uid) }
                prefs.deviceKey = key
                prefs.registeredToken = token
                prefs.userEmail = email
                prefs.keyCreatedByApp = true
                toast(R.string.connected)
                if (!hasNotificationPermission()) requestNotifications()
            } catch (e: Exception) {
                toast(getString(R.string.sign_in_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                signInButton.isEnabled = true
                updateConnection()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateRegion()
        updateConnection()
        updateSetupButtons()
        loadStatus()
        refreshRegistration()
    }

    private fun connect() {
        val key = keyInput.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            keyInput.error = getString(R.string.key_required)
            return
        }

        connectButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                withContext(Dispatchers.IO) { ApiClient.registerDevice(key, token, Prefs.deviceName, prefs.region.uid) }
                prefs.deviceKey = key
                prefs.registeredToken = token
                prefs.userEmail = null
                prefs.keyCreatedByApp = false
                keyInput.setText("")
                toast(R.string.connected)
                if (!hasNotificationPermission()) requestNotifications()
            } catch (e: Exception) {
                toast(getString(R.string.connect_failed, e.message ?: e.javaClass.simpleName))
            } finally {
                connectButton.isEnabled = true
                updateConnection()
            }
        }
    }

    private fun disconnect() {
        val key = prefs.deviceKey
        val token = prefs.registeredToken
        val revokeKey = prefs.keyCreatedByApp
        prefs.clearConnection()
        updateConnection()
        if (key != null) {
            lifecycleScope.launch {
                // Best effort: if it fails, the phone stays in Mind's list until it is removed there.
                runCatching { withContext(Dispatchers.IO) { ApiClient.unregisterDevice(key, token, revokeKey) } }
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
        statusText.text = getString(R.string.status_unknown)
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
            val text = try {
                val status = withContext(Dispatchers.IO) { ApiClient.getStatus(regionUid) }
                describe(status)
            } catch (e: Exception) {
                getString(R.string.status_unknown)
            }
            statusText.text = text
        }
    }

    private fun describe(status: AlertStatus): String {
        if (!status.isConfigured || status.isActive == null) {
            return getString(R.string.status_unknown)
        }
        if (!status.isActive) {
            return getString(R.string.status_calm)
        }
        val kind = getString(
            when (status.kind) {
                "drones" -> R.string.status_drones
                "missiles" -> R.string.status_missiles
                else -> R.string.status_general
            }
        )
        val since = status.since?.let { formatTime(it) } ?: return kind
        return getString(R.string.status_since, kind, since)
    }

    private fun formatTime(iso: String): String? = runCatching {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
    }.getOrNull()

    private fun updateConnection() {
        val connected = prefs.isConnected
        val email = prefs.userEmail
        connectionText.text = when {
            connected && email != null -> getString(R.string.connection_on_as, email)
            connected -> getString(R.string.connection_on)
            else -> getString(R.string.connection_off)
        }
        connectionText.setTextColor(ContextCompat.getColor(this, if (connected) R.color.calm else R.color.danger))
        signInGroup.visibility = if (connected) View.GONE else View.VISIBLE
        if (connected) keyGroup.visibility = View.GONE
        disconnectButton.visibility = if (connected) View.VISIBLE else View.GONE
        // Signed out, the phone gets no alerts: only signing in is offered.
        regionCard.visibility = if (connected) View.VISIBLE else View.GONE
        setupCard.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun updateSetupButtons() {
        notificationsButton.visibility = if (hasNotificationPermission()) View.GONE else View.VISIBLE
        val power = getSystemService(PowerManager::class.java)
        val batteryVisibility = if (power.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE
        batteryButton.visibility = batteryVisibility
        batteryHint.visibility = batteryVisibility
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // An alert app is exactly the case this exemption exists for: a delayed alert is useless.
    // Google Play restricts the direct one-tap exemption request, and the optimisation list
    // opens filtered to apps already exempt, so this opens Dio Alert's own settings page,
    // where Battery → Unrestricted is on every maker's Android.
    /** Where "unrestricted" is on this phone's settings page: it moved between Android versions, and Samsung and Xiaomi have their own. */
    private fun batteryHintRes(): Int {
        val maker = Build.MANUFACTURER.lowercase()
        return when {
            maker == "samsung" -> R.string.battery_hint_samsung
            maker in setOf("xiaomi", "redmi", "poco") -> R.string.battery_hint_xiaomi
            Build.VERSION.SDK_INT >= 34 -> R.string.battery_hint_14
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> R.string.battery_hint_12
            else -> R.string.battery_hint_8
        }
    }

    private fun requestBatteryExemption() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
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

    private companion object {
        const val ALERTS_SOURCE_URL = "https://alerts.in.ua/"
        /** Mind's Dio Alert guide: the battery setting per Android version and maker, and what to check when there is no sound. */
        const val SETUP_GUIDE_URL = "https://mind.dio-quiz.com.ua/guides?tab=dioAlert"
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
