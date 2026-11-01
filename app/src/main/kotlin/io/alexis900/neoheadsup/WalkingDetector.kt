package io.alexis900.neoheadsup

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

/** Wrapper around the Activity Recognition Transition API. */
object WalkingDetector {

    private const val ACTION_TRANSITION =
        "io.alexis900.neoheadsup.WALKING_TRANSITION"
    private const val REQUEST_CODE = 1001

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WalkingTransitionReceiver::class.java)
            .setAction(ACTION_TRANSITION)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun register(context: Context): Task<Void> {
        if (context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Tasks.forException(
                SecurityException("ACTIVITY_RECOGNITION permission not granted")
            )
        }

        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
        val request = ActivityTransitionRequest(transitions)
        return try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(request, pendingIntent(context))
        } catch (exception: SecurityException) {
            Tasks.forException(exception)
        }
    }

    fun unregister(context: Context): Task<Void> {
        if (context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Nothing to remove if the permission was revoked; avoid crashing.
            return Tasks.forResult(null)
        }
        return try {
            ActivityRecognition.getClient(context)
                .removeActivityTransitionUpdates(pendingIntent(context))
        } catch (exception: SecurityException) {
            Tasks.forException(exception)
        }
    }
}
