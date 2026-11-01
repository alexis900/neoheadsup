package io.alexis900.neoheadsup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Schedules the next confirmation, stop or expiry check.
 *
 * The delay always comes from the pure [io.alexis900.neoheadsup.detection.SessionController],
 * so an alarm only exists while there is real work to do. No alarm is kept for an idle
 * feature.
 *
 * A confirmation window is short and user-visible. An inexact `AlarmManager` alarm can be
 * delayed by several seconds, so the same tick is also posted on the main looper while the
 * process is alive. The alarm remains the fallback if Android kills the process. Both paths
 * are idempotent: the state machine rejects a tick that has nothing to change.
 */
object RecheckScheduler {

    private const val ACTION_RECHECK = "io.alexis900.neoheadsup.RECHECK"
    private const val REQUEST_CODE = 200

    private val handler = Handler(Looper.getMainLooper())
    private var inProcessTick: Runnable? = null

    fun scheduleNext(context: Context) {
        val delay = DetectionEngine.nextTickDelayMs(context)
        if (delay == null) {
            cancel(context)
        } else {
            scheduleIn(context, delay)
        }
    }

    fun cancel(context: Context) {
        inProcessTick?.let(handler::removeCallbacks)
        inProcessTick = null
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    private fun scheduleIn(context: Context, delayMs: Long) {
        val appContext = context.applicationContext

        inProcessTick?.let(handler::removeCallbacks)
        val runnable = Runnable {
            DetectionEngine.onTick(appContext, "handler")
        }
        inProcessTick = runnable
        handler.postDelayed(runnable, delayMs.coerceAtLeast(0L))

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + delayMs.coerceAtLeast(0L),
            pendingIntent(context)
        )
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RecheckReceiver::class.java)
            .setAction(ACTION_RECHECK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isRecheckAction(action: String?): Boolean = action == ACTION_RECHECK
}
