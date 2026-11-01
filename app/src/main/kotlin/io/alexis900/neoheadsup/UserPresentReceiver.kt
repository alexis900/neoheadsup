package io.alexis900.neoheadsup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Unlocking the phone is a useful recheck point: it can confirm a pending walk, close an
 * expired session and show a reminder that could not be shown while the screen was off.
 */
class UserPresentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        DetectionEngine.onUserPresent(context)
    }
}
