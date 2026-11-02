package io.alexis900.neoheadsup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import io.alexis900.neoheadsup.detection.SessionConfig
import io.alexis900.neoheadsup.detection.SessionState

class MainActivity : AppCompatActivity() {

    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var textStatus: TextView
    private lateinit var textActivityState: TextView
    private lateinit var textNotificationState: TextView
    private lateinit var textDiagnostics: TextView
    private lateinit var buttonActivity: Button
    private lateinit var buttonNotifications: Button
    private lateinit var buttonChannelSettings: Button
    private lateinit var buttonTest: Button
    private lateinit var spinnerCooldown: MaterialAutoCompleteTextView
    private lateinit var buttonSettings: View
    private var updatingSwitch = false
    private var appliedThemeMode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        NotificationHelper.createChannel(this)
        Prefs.removeLegacySession(this)
        appliedThemeMode = Prefs.getThemeMode(this)

        switchEnabled = findViewById(R.id.switch_enabled)
        textStatus = findViewById(R.id.text_status)
        textActivityState = findViewById(R.id.text_activity_state)
        textNotificationState = findViewById(R.id.text_notification_state)
        textDiagnostics = findViewById(R.id.text_diagnostics)
        buttonActivity = findViewById(R.id.button_activity)
        buttonNotifications = findViewById(R.id.button_notifications)
        buttonChannelSettings = findViewById(R.id.button_channel_settings)
        buttonTest = findViewById(R.id.button_test)
        spinnerCooldown = findViewById(R.id.spinner_cooldown)
        buttonSettings = findViewById(R.id.button_settings)

        setupCooldownSpinner()
        setupNavigation()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        if (appliedThemeMode != Prefs.getThemeMode(this)) {
            recreate()
            return
        }
        // Expire stale state and confirm a walk whose 15 second window elapsed while the
        // activity was in the background.
        DetectionEngine.onTick(this, "activity_resume")
        updateUi()
    }

    private fun setupCooldownSpinner() {
        val labels = COOLDOWN_MINUTES.map {
            getString(R.string.cooldown_option, it)
        }.toTypedArray()

        val current = Prefs.getCooldownMinutes(this)
        val position = COOLDOWN_MINUTES.indexOf(current).takeIf { it >= 0 }
            ?: COOLDOWN_MINUTES.indexOf(SessionConfig.DEFAULT_COOLDOWN_MINUTES)
                .coerceAtLeast(0)

        spinnerCooldown.setSimpleItems(labels)
        spinnerCooldown.setText(labels[position], false)
        spinnerCooldown.setOnItemClickListener { _, _, selected, _ ->
            Prefs.setCooldownMinutes(this@MainActivity, COOLDOWN_MINUTES[selected])
            updateUi()
        }
    }

    private fun setupNavigation() {
        buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupListeners() {
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (updatingSwitch) return@setOnCheckedChangeListener

            if (isChecked) {
                if (!hasActivityPermission() || !notificationsReady()) {
                    setSwitchSilently(false)
                    requestMissingPermissions()
                    return@setOnCheckedChangeListener
                }
                enableHeadsUp()
            } else {
                disableHeadsUp()
            }
        }

        buttonActivity.setOnClickListener {
            requestPermissions(
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                REQ_PERMISSIONS
            )
        }

        buttonNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_PERMISSIONS
                )
            } else {
                openNotificationSettings()
            }
        }

        buttonChannelSettings.setOnClickListener { openNotificationSettings() }

        buttonTest.setOnClickListener {
            NotificationHelper.show(this)
            Prefs.markNotified(this)
            Diag.log(this, "test_notification")
            Toast.makeText(this, R.string.test_sent, Toast.LENGTH_SHORT).show()
            updateUi()
        }
    }

    private fun enableHeadsUp() {
        Prefs.setEnabled(this, true)
        Prefs.clearSnooze(this)
        DetectionEngine.onEnabled(this)
        WalkingDetector.register(this).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Prefs.setEnabled(this, false)
                DetectionEngine.onDisabled(this)
                Toast.makeText(this, R.string.enable_failed, Toast.LENGTH_LONG).show()
            }
            updateUi()
        }
    }

    private fun disableHeadsUp() {
        Prefs.setEnabled(this, false)
        DetectionEngine.onDisabled(this)
        WalkingDetector.unregister(this)
        updateUi()
    }

    private fun updateUi() {
        val activityGranted = hasActivityPermission()
        val notificationPermission = hasNotificationPermission()
        val channelBlocked = isChannelBlocked()
        val notificationsGranted = notificationPermission && !channelBlocked
        val enabled = Prefs.isEnabled(this)
        val ready = activityGranted && notificationsGranted
        val snapshot = DetectionEngine.snapshot(this)
        val cooldown = Prefs.getCooldownMinutes(this)

        setSwitchSilently(enabled && ready)

        when {
            !ready -> {
                textStatus.setText(R.string.status_missing_permissions)
                textStatus.setTextColor(getColor(R.color.error))
            }
            enabled && snapshot.state == SessionState.WALKING -> {
                textStatus.text = getString(R.string.status_walking, cooldown)
                textStatus.setTextColor(getColor(R.color.success))
            }
            enabled && snapshot.pendingEnterSinceMs > 0L -> {
                textStatus.setText(R.string.status_pending)
                textStatus.setTextColor(getColor(R.color.success))
            }
            enabled -> {
                textStatus.text = getString(R.string.status_active, cooldown)
                textStatus.setTextColor(getColor(R.color.success))
            }
            else -> {
                textStatus.setText(R.string.status_off)
                textStatus.setTextColor(getColor(R.color.text_secondary))
            }
        }

        textDiagnostics.text =
            Diag.latest(this) ?: getString(R.string.diagnostics_empty)

        textActivityState.setText(
            if (activityGranted) R.string.permission_granted
            else R.string.permission_required
        )
        textActivityState.setTextColor(
            getColor(if (activityGranted) R.color.success else R.color.error)
        )
        buttonActivity.setText(
            if (activityGranted) R.string.permission_granted
            else R.string.grant_permission
        )
        buttonActivity.isEnabled = !activityGranted

        when {
            !notificationPermission -> {
                textNotificationState.setText(R.string.permission_required)
                textNotificationState.setTextColor(getColor(R.color.error))
                buttonNotifications.setText(R.string.grant_permission)
            }
            channelBlocked -> {
                textNotificationState.setText(R.string.notifications_blocked)
                textNotificationState.setTextColor(getColor(R.color.error))
                buttonNotifications.setText(R.string.open_settings)
            }
            else -> {
                textNotificationState.setText(R.string.permission_granted)
                textNotificationState.setTextColor(getColor(R.color.success))
                buttonNotifications.setText(R.string.open_settings)
            }
        }
        buttonNotifications.isEnabled = true
        buttonChannelSettings.visibility = if (channelBlocked) View.VISIBLE else View.GONE
    }

    private fun setSwitchSilently(checked: Boolean) {
        updatingSwitch = true
        switchEnabled.isChecked = checked
        updatingSwitch = false
    }

    private fun hasActivityPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun notificationsReady(): Boolean =
        hasNotificationPermission() && !isChannelBlocked()

    private fun isChannelBlocked(): Boolean {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        val channel: NotificationChannel? =
            manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        return channel?.importance == NotificationManager.IMPORTANCE_NONE
    }

    private fun requestMissingPermissions() {
        val missing = mutableListOf<String>()
        if (!hasActivityPermission()) {
            missing.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        when {
            missing.isNotEmpty() ->
                requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
            isChannelBlocked() -> showNotificationSettingsDialog()
            else -> Toast.makeText(
                this,
                R.string.permissions_needed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMISSIONS) return

        updateUi()
        if (hasActivityPermission() && notificationsReady()) {
            Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_LONG).show()
        } else {
            showPermissionSettingsDialogIfPermanentlyDenied()
        }
    }

    private fun showPermissionSettingsDialogIfPermanentlyDenied() {
        val message: String? = when {
            !hasActivityPermission() &&
                !shouldShowRequestPermissionRationale(
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) -> getString(R.string.permission_settings_activity)

            Build.VERSION.SDK_INT >= 33 &&
                !hasNotificationPermission() &&
                !shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> getString(R.string.permission_settings_notifications)

            else -> null
        } ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openAppSettings() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNotificationSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_dialog_title)
            .setMessage(R.string.notification_settings_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                openNotificationSettings()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (exception: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }

    private companion object {
        const val REQ_PERMISSIONS = 42
        val COOLDOWN_MINUTES = intArrayOf(5, 10, 15, 30)
    }
}
