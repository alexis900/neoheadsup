package io.alexis900.neoheadsup

import android.content.Context
import io.alexis900.neoheadsup.detection.AlertDecision
import io.alexis900.neoheadsup.detection.DetectionEvent
import io.alexis900.neoheadsup.detection.SessionController
import io.alexis900.neoheadsup.detection.SessionSnapshot
import io.alexis900.neoheadsup.detection.SessionState
import io.alexis900.neoheadsup.detection.SessionUpdate
import com.google.android.gms.location.DetectedActivity

/**
 * Android-facing glue around the pure [SessionController].
 *
 * Responsibilities: load/save the snapshot, schedule the confirmation and recheck alarms,
 * ask [HeadsUpNotifier] to evaluate an alert, and write a diagnostic line for every
 * decision.
 */
object DetectionEngine {

    private val controller = SessionController()

    fun snapshot(context: Context): SessionSnapshot = SessionStore.load(context)

    fun onEnabled(context: Context) {
        val update = controller.onEnabled(SessionStore.load(context))
        persist(context, "enable", update)
        RecheckScheduler.scheduleNext(context)
    }

    fun onDisabled(context: Context) {
        val update = controller.onDisabled(SessionStore.load(context))
        persist(context, "disable", update)
        RecheckScheduler.cancel(context)
        NotificationHelper.cancel(context)
    }

    /**
     * Clears the current session but keeps the feature enabled (snooze or manual reset).
     */
    fun resetSession(context: Context, reason: String) {
        val update = controller.onEnabled(SessionStore.load(context))
        SessionStore.save(context, update.snapshot)
        Diag.log(
            context,
            "reset reason=$reason old=${update.previousState} new=${update.newState}"
        )
        RecheckScheduler.cancel(context)
        NotificationHelper.cancel(context)
    }

    /**
     * A reboot invalidates elapsed-realtime ordering and all evidence collected before it.
     */
    fun onReboot(context: Context) {
        val update = controller.onReboot(
            SessionStore.load(context),
            Prefs.isEnabled(context)
        )
        persist(context, "reboot", update)
        RecheckScheduler.cancel(context)
        NotificationHelper.cancel(context)
    }

    fun onPackageReplaced(context: Context) {
        // The device did not reboot, so elapsed-realtime ordering is still valid. Just
        // expire anything stale and make sure an alarm exists if one is due.
        runTick(context, "package_replaced")
    }

    fun onTransition(context: Context, event: DetectionEvent) {
        val enabled = Prefs.isEnabled(context)
        val update = controller.onEvent(SessionStore.load(context), event, enabled)
        SessionStore.save(context, update.snapshot)
        Diag.log(
            context,
            "event=${event.transition} activity=${activityName(event.activityType)} " +
                "old=${update.previousState} new=${update.newState} " +
                "accepted=${update.accepted} reason=${update.reason}"
        )

        if (update.snapshot.state == SessionState.WALKING &&
            update.snapshot.pendingStopSinceMs == 0L
        ) {
            HeadsUpNotifier.maybeNotify(context)
        }
        RecheckScheduler.scheduleNext(context)
    }

    fun onTick(context: Context, source: String = "alarm") {
        runTick(context, source)
    }

    fun onUserPresent(context: Context) {
        runTick(context, "user_present")
    }

    /** Delay until the next confirmation / expiry check, or null when none is needed. */
    fun nextTickDelayMs(context: Context): Long? =
        controller.nextTickDelayMs(
            SessionStore.load(context),
            System.currentTimeMillis(),
            Prefs.isEnabled(context)
        )

    private fun runTick(context: Context, source: String) {
        val enabled = Prefs.isEnabled(context)
        val update = controller.onTick(
            SessionStore.load(context),
            System.currentTimeMillis(),
            enabled
        )
        SessionStore.save(context, update.snapshot)
        Diag.log(
            context,
            "tick source=$source old=${update.previousState} " +
                "new=${update.newState} accepted=${update.accepted} " +
                "reason=${update.reason}"
        )

        if (update.snapshot.state == SessionState.WALKING) {
            HeadsUpNotifier.maybeNotify(context)
        }
        RecheckScheduler.scheduleNext(context)
    }

    private fun persist(context: Context, source: String, update: SessionUpdate) {
        SessionStore.save(context, update.snapshot)
        Diag.log(
            context,
            "$source old=${update.previousState} new=${update.newState} " +
                "reason=${update.reason}"
        )
    }

    private fun activityName(activityType: Int): String = when (activityType) {
        DetectedActivity.WALKING -> "WALKING"
        DetectedActivity.STILL -> "STILL"
        DetectedActivity.RUNNING -> "RUNNING"
        DetectedActivity.ON_FOOT -> "ON_FOOT"
        DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
        DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
        else -> "TYPE_$activityType"
    }

    fun decisionForLog(decision: AlertDecision): String =
        "allowed=${decision.allowed} reason=${decision.reason}"
}
