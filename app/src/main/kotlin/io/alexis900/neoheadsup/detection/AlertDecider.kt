package io.alexis900.neoheadsup.detection

data class AlertContext(
    val enabled: Boolean,
    val screenOnAndUnlocked: Boolean,
    val snoozed: Boolean,
    val cooldownMs: Long,
    val lastNotificationMs: Long,
    val nowMs: Long,
    val config: SessionConfig = SessionConfig()
)

data class AlertDecision(
    val allowed: Boolean,
    val reason: String
)

/**
 * Pure decision policy for generating an alert. It never trusts an old persisted session:
 * the walking state must be confirmed and its last evidence must be recent.
 */
object AlertDecider {

    fun decide(snapshot: SessionSnapshot, context: AlertContext): AlertDecision {
        if (!context.enabled) return deny("disabled")
        if (snapshot.state != SessionState.WALKING) {
            return deny("state_${snapshot.state.name.lowercase()}")
        }
        if (snapshot.pendingStopSinceMs > 0L) return deny("stop_pending")
        if (snapshot.lastEvidenceMs <= 0L) return deny("no_walking_evidence")

        val evidenceAge = context.nowMs - snapshot.lastEvidenceMs
        if (evidenceAge < 0L || evidenceAge > context.config.sessionMaxEvidenceMs) {
            return deny("stale_evidence")
        }
        if (context.snoozed) return deny("snoozed")
        if (!context.screenOnAndUnlocked) return deny("screen_off_or_locked")
        if (context.lastNotificationMs > 0L &&
            context.nowMs - context.lastNotificationMs < context.cooldownMs
        ) {
            return deny("cooldown")
        }
        return AlertDecision(allowed = true, reason = "allowed")
    }

    private fun deny(reason: String) = AlertDecision(allowed = false, reason = reason)
}
