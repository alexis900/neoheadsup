package io.alexis900.neoheadsup

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import io.alexis900.neoheadsup.detection.AlertContext
import io.alexis900.neoheadsup.detection.AlertDecider
import io.alexis900.neoheadsup.detection.AlertDecision
import io.alexis900.neoheadsup.detection.SessionState

/**
 * Evaluates the pure alert policy and, when allowed, posts the reminder.
 *
 * Nothing is shown unless the session is confirmed as walking and its last walking
 * evidence is recent. A persisted session from a previous process is never trusted on its
 * own.
 */
object HeadsUpNotifier {

    fun maybeNotify(context: Context): AlertDecision {
        val snapshot = SessionStore.load(context)
        val now = System.currentTimeMillis()
        val decision = AlertDecider.decide(
            snapshot,
            AlertContext(
                enabled = Prefs.isEnabled(context),
                screenOnAndUnlocked = isScreenOnAndUnlocked(context),
                snoozed = Prefs.isSnoozed(context),
                cooldownMs = Prefs.getCooldownMinutes(context) * 60_000L,
                lastNotificationMs = Prefs.getLastNotification(context),
                nowMs = now
            )
        )

        val evidenceAge = if (snapshot.lastEvidenceMs > 0L) {
            (now - snapshot.lastEvidenceMs).toString()
        } else {
            "none"
        }
        Diag.log(
            context,
            "alert state=${snapshot.state} evidenceAgeMs=$evidenceAge " +
                DetectionEngine.decisionForLog(decision)
        )

        if (decision.allowed) {
            NotificationHelper.show(context)
            Prefs.markNotified(context)
        }
        return decision
    }

    fun isScreenOnAndUnlocked(context: Context): Boolean {
        val powerManager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
        if (!powerManager.isInteractive) return false

        val keyguardManager =
            context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager == null || !keyguardManager.isKeyguardLocked
    }

    /** True while a confirmed session still exists; used by the UI summary. */
    fun isWalking(context: Context): Boolean =
        SessionStore.load(context).state == SessionState.WALKING
}
