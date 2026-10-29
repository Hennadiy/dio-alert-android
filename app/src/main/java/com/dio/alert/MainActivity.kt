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
        signInButton.setOnClickListener { if (isRegistering) register() else signIn() }
        modeButton.setOnClickListener { setRegistering(!isRegistering) }
        forgotButton.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.MIND_URL + "forgot-password"))) }
        disconnectButton.setOnClickListener { disconnect() }
        notificationsButton.setOnClickListener { requestNotifications() }
        batteryButton.setOnClickListener { requestBatteryExemption() }
        findViewById<MaterialButton>(R.id.channelSettingsButton).setOnClickListener { openNotificationSettings() }

        // Android 13+ shows nothing until the user allows notifications; ask as soon as alerts can arrive.
        if (prefs.isConnected && !hasNotificationPermission()) {
            requestNotifications()
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
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }
}
