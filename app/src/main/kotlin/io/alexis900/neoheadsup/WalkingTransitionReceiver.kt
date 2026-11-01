package io.alexis900.neoheadsup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.alexis900.neoheadsup.detection.DetectionEvent
import io.alexis900.neoheadsup.detection.TransitionType
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Receives walking / not-walking transitions from Google Play Services and forwards them to
 * the detection state machine.
 *
 * A WALKING enter does not produce a reminder directly: it starts a 15 second confirmation
 * window that an exit can cancel. This is what removes the false positives that happened
 * when the transition API briefly reported WALKING while the user was seated.
 */
class WalkingTransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return

        for (event in result.transitionEvents) {
            if (event.activityType != DetectedActivity.WALKING) continue

            val transition = when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> TransitionType.ENTER
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> TransitionType.EXIT
                else -> continue
            }

            DetectionEngine.onTransition(
                context,
                DetectionEvent(
                    transition = transition,
                    activityType = event.activityType,
                    wallClockMs = System.currentTimeMillis(),
                    elapsedRealtimeNanos = event.elapsedRealTimeNanos
                )
            )
        }
    }
}
