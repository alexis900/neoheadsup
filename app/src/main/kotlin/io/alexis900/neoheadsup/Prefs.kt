package io.alexis900.neoheadsup

import android.content.Context
import io.alexis900.neoheadsup.detection.SessionConfig

/**
 * Small, private preferences store. Nothing leaves the device.
 *
 * The file name is independent of the previous app identity. Because the package was renamed,
 * no data from the old installation is reused.
 */
object Prefs {

    private const val FILE = "neoheadsup_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
    private const val KEY_LAST_NOTIFICATION = "last_notification"
    private const val KEY_SNOOZE_UNTIL = "snooze_until"

    /** Removed in NeoHeadsUp; kept only to clean it from upgraded installs. */
    private const val LEGACY_KEY_WALKING_SINCE = "walking_since"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getCooldownMinutes(context: Context): Int =
        sp(context).getInt(KEY_COOLDOWN_MINUTES, SessionConfig.DEFAULT_COOLDOWN_MINUTES)

    fun setCooldownMinutes(context: Context, minutes: Int) {
        sp(context).edit().putInt(KEY_COOLDOWN_MINUTES, minutes).apply()
    }

    fun getLastNotification(context: Context): Long =
        sp(context).getLong(KEY_LAST_NOTIFICATION, 0L)

    fun markNotified(context: Context) {
        sp(context).edit()
            .putLong(KEY_LAST_NOTIFICATION, System.currentTimeMillis())
            .apply()
    }

    fun clearLastNotification(context: Context) {
        sp(context).edit().remove(KEY_LAST_NOTIFICATION).apply()
    }

    fun getSnoozeUntil(context: Context): Long =
        sp(context).getLong(KEY_SNOOZE_UNTIL, 0L)

    fun snooze(context: Context, minutes: Int) {
        val until = System.currentTimeMillis() + minutes * 60_000L
        sp(context).edit().putLong(KEY_SNOOZE_UNTIL, until).apply()
    }

    fun isSnoozed(context: Context): Boolean =
        System.currentTimeMillis() < getSnoozeUntil(context)

    fun clearSnooze(context: Context) {
        sp(context).edit().remove(KEY_SNOOZE_UNTIL).apply()
    }

    /**
     * Deletes the old wall-clock walking timestamp, if any. This is harmless on a fresh
     * install and keeps the cleanup explicit for devices that shared the old package.
     */
    fun removeLegacySession(context: Context) {
        sp(context).edit().remove(LEGACY_KEY_WALKING_SINCE).apply()
    }
}
