package io.alexis900.neoheadsup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recovers after a reboot or an app update.
 *
 * A reboot invalidates elapsed-realtime ordering and every piece of walking evidence from
 * the previous boot, so the session is reset instead of being resumed. An app update keeps
 * the session because the device clock did not change.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            DetectionEngine.onReboot(context)
        } else {
            DetectionEngine.onPackageReplaced(context)
        }

        if (!Prefs.isEnabled(context)) return

        val pendingResult = goAsync()
        WalkingDetector.register(context).addOnCompleteListener {
            pendingResult.finish()
        }
    }
}
