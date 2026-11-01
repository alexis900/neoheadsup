package io.alexis900.neoheadsup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Runs a confirmation / stop / expiry tick for the detection state machine. */
class RecheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!RecheckScheduler.isRecheckAction(intent.action)) return
        DetectionEngine.onTick(context, "alarm")
    }
}
