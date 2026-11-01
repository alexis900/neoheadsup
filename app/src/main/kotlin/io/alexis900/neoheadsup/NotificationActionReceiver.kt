package io.alexis900.neoheadsup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the notification actions. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> {
                // "Not walking": dismiss the session and pause reminders without
                // unregistering activity recognition.
                Prefs.snooze(context, 30)
                DetectionEngine.resetSession(context, "snoozed")
            }
            ACTION_DISABLE -> {
                Prefs.setEnabled(context, false)
                DetectionEngine.onDisabled(context)
                WalkingDetector.unregister(context)
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "io.alexis900.neoheadsup.SNOOZE"
        const val ACTION_DISABLE = "io.alexis900.neoheadsup.DISABLE"
    }
}
