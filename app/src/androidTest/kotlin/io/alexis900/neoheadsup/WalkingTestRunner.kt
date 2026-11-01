package io.alexis900.neoheadsup

import android.app.Activity
import android.app.Instrumentation
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.PowerManager
import android.os.SystemClock
import io.alexis900.neoheadsup.detection.SessionState
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Device smoke test for the real notification path, including the confirmation windows:
 *
 *  - A synthetic WALKING enter must NOT notify immediately. After the 15 s confirmation the
 *    state must become WALKING and the reminder notification must appear.
 *  - A synthetic WALKING exit must keep the state for 10 s and then return to IDLE.
 *  - Both confirmation windows are checked with a tolerance so an inexact alarm cannot
 *    silently turn a 15 s confirmation into a 26 s one.
 *
 * Run with:
 *   adb shell am instrument -w \
 *     io.alexis900.neoheadsup.test/io.alexis900.neoheadsup.WalkingTestRunner
 */
class WalkingTestRunner : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val context = targetContext
        val result = Bundle()
        try {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            if (powerManager?.isInteractive != true ||
                keyguardManager?.isKeyguardLocked != false
            ) {
                result.putString(
                    "result",
                    "SKIPPED: wake and unlock the device before running this test"
                )
                finish(Activity.RESULT_OK, result)
                return
            }

            val notificationManager =
                context.getSystemService(NotificationManager::class.java)

            // Clean slate: no session, no snooze, no cooldown.
            SessionStore.clear(context)
            Prefs.setEnabled(context, true)
            Prefs.clearSnooze(context)
            Prefs.clearLastNotification(context)
            notificationManager.cancelAll()

            sendTransition(context, ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            val enterAtMs = SystemClock.uptimeMillis()

            // The reminder must not appear during the confirmation window.
            val immediateNotification = hasNotification(context)
            val walking = awaitState(context, SessionState.WALKING, 30_000L)
            val confirmationLatencyMs = SystemClock.uptimeMillis() - enterAtMs
            val notified = awaitNotification(context, 10_000L)
            val walkingSnapshot = SessionStore.load(context)

            sendTransition(context, ActivityTransition.ACTIVITY_TRANSITION_EXIT)
            val exitAtMs = SystemClock.uptimeMillis()

            val closed = awaitState(context, SessionState.IDLE, 20_000L)
            val stopLatencyMs = SystemClock.uptimeMillis() - exitAtMs
            val closedSnapshot = SessionStore.load(context)

            // The in-process handler should hit the 15 s / 10 s windows. Allow a generous
            // margin for slow devices, but fail if the inexact alarm is used alone again.
            val timingOk = confirmationLatencyMs in 14_000L..22_000L &&
                stopLatencyMs in 9_000L..16_000L

            val ok = !immediateNotification &&
                walking &&
                notified &&
                walkingSnapshot.lastEvidenceMs > 0L &&
                closed &&
                closedSnapshot.lastEvidenceMs == 0L &&
                timingOk

            result.putString(
                "result",
                "immediateNotification=$immediateNotification " +
                    "walkingConfirmed=$walking notified=$notified " +
                    "confirmationLatencyMs=$confirmationLatencyMs " +
                    "stopLatencyMs=$stopLatencyMs timingOk=$timingOk " +
                    "walkingSince=${walkingSnapshot.walkingSinceMs} " +
                    "exitClosed=$closed " +
                    "lastDiagnostic=${Diag.latest(context)}"
            )

            // Leave the device clean: enabled, no session, no test notification.
            notificationManager.cancelAll()
            Prefs.clearLastNotification(context)
            DetectionEngine.resetSession(context, "instrumentation_cleanup")

            finish(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        } catch (throwable: Throwable) {
            result.putString("result", "exception: ${throwable.stackTraceToString()}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun sendTransition(context: Context, transitionType: Int) {
        val transition = ActivityTransitionResult(
            listOf(
                ActivityTransitionEvent(
                    DetectedActivity.WALKING,
                    transitionType,
                    SystemClock.elapsedRealtimeNanos()
                )
            )
        )
        val parcel = Parcel.obtain()
        transition.writeToParcel(parcel, 0)
        val payload = parcel.marshall()
        parcel.recycle()

        context.sendBroadcast(
            Intent(context, WalkingTransitionReceiver::class.java)
                .putExtra(KEY_TRANSITION_RESULT, payload)
        )
    }

    private fun awaitState(
        context: Context,
        expected: SessionState,
        timeoutMs: Long
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (SessionStore.load(context).state == expected) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun awaitNotification(context: Context, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (hasNotification(context)) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun hasNotification(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications.any { it.packageName == context.packageName }
    }

    private companion object {
        const val KEY_TRANSITION_RESULT =
            "com.google.android.location.internal.EXTRA_ACTIVITY_TRANSITION_RESULT"
    }
}
