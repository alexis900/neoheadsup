package io.alexis900.neoheadsup.detection

/**
 * Pure state machine for walking detection. It has no Android dependencies so it can be
 * covered by fast JVM unit tests.
 *
 * Accepted state model:
 *  - OFF: the feature is disabled.
 *  - IDLE: enabled, no confirmed walking; a WALKING enter may be pending confirmation.
 *  - WALKING: walking was confirmed; a stop may be pending confirmation.
 */
class SessionController(
    val config: SessionConfig = SessionConfig()
) {

    fun onEnabled(current: SessionSnapshot): SessionUpdate =
        SessionUpdate(
            previousSnapshot = current,
            snapshot = SessionSnapshot(state = SessionState.IDLE),
            accepted = true,
            reason = "enabled"
        )

    fun onDisabled(current: SessionSnapshot): SessionUpdate =
        SessionUpdate(
            previousSnapshot = current,
            snapshot = SessionSnapshot(state = SessionState.OFF),
            accepted = true,
            reason = "disabled"
        )

    /**
     * A reboot invalidates elapsed-realtime ordering and any evidence collected before it.
     */
    fun onReboot(current: SessionSnapshot, enabled: Boolean): SessionUpdate =
        SessionUpdate(
            previousSnapshot = current,
            snapshot = SessionSnapshot(
                state = if (enabled) SessionState.IDLE else SessionState.OFF
            ),
            accepted = true,
            reason = "reboot"
        )

    fun onEvent(
        current: SessionSnapshot,
        event: DetectionEvent,
        enabled: Boolean
    ): SessionUpdate {
        if (!enabled) {
            return SessionUpdate(
                previousSnapshot = current,
                snapshot = SessionSnapshot(state = SessionState.OFF),
                accepted = false,
                reason = "feature_disabled"
            )
        }

        var snapshot = if (current.state == SessionState.OFF) {
            current.copy(state = SessionState.IDLE)
        } else {
            current
        }

        if (event.elapsedRealtimeNanos > 0 && snapshot.lastEventElapsedNanos > 0) {
            when {
                event.elapsedRealtimeNanos < snapshot.lastEventElapsedNanos ->
                    return SessionUpdate(
                        previousSnapshot = current,
                        snapshot = snapshot,
                        accepted = false,
                        reason = "out_of_order"
                    )

                event.elapsedRealtimeNanos == snapshot.lastEventElapsedNanos ->
                    return SessionUpdate(
                        previousSnapshot = current,
                        snapshot = snapshot,
                        accepted = false,
                        reason = "duplicate_event"
                    )
            }
        }

        snapshot = expireIfStale(snapshot, event.wallClockMs)
        val base = snapshot.copy(
            lastEventElapsedNanos = maxOf(
                snapshot.lastEventElapsedNanos,
                event.elapsedRealtimeNanos
            )
        )

        val next: SessionSnapshot
        val reason: String

        when (event.transition) {
            TransitionType.ENTER -> when (snapshot.state) {
                SessionState.WALKING -> {
                    if (snapshot.pendingStopSinceMs > 0) {
                        next = base.copy(
                            pendingStopSinceMs = 0L,
                            lastEvidenceMs = event.wallClockMs
                        )
                        reason = "stop_cancelled"
                    } else {
                        next = base.copy(lastEvidenceMs = event.wallClockMs)
                        reason = "walking_evidence_refreshed"
                    }
                }

                else -> {
                    if (snapshot.pendingEnterSinceMs > 0) {
                        // Repeated evidence must not indefinitely postpone confirmation.
                        next = base.copy(lastEvidenceMs = event.wallClockMs)
                        reason = "walking_evidence_pending"
                    } else {
                        next = base.copy(
                            pendingEnterSinceMs = event.wallClockMs,
                            pendingStopSinceMs = 0L,
                            lastEvidenceMs = event.wallClockMs
                        )
                        reason = "walking_confirmation_started"
                    }
                }
            }

            TransitionType.EXIT -> when (snapshot.state) {
                SessionState.WALKING -> {
                    next = base.copy(
                        pendingStopSinceMs =
                            if (snapshot.pendingStopSinceMs > 0) {
                                snapshot.pendingStopSinceMs
                            } else {
                                event.wallClockMs
                            }
                    )
                    reason = if (snapshot.pendingStopSinceMs > 0) {
                        "stop_confirmation_pending"
                    } else {
                        "stop_confirmation_started"
                    }
                }

                else -> {
                    next = base.copy(
                        pendingEnterSinceMs = 0L,
                        pendingStopSinceMs = 0L,
                        lastEvidenceMs = 0L
                    )
                    reason = "stop_before_walking_confirmed"
                }
            }
        }

        return SessionUpdate(
            previousSnapshot = current,
            snapshot = next,
            accepted = true,
            reason = reason
        )
    }

    fun onTick(
        current: SessionSnapshot,
        nowMs: Long,
        enabled: Boolean
    ): SessionUpdate {
        if (!enabled) {
            return SessionUpdate(
                previousSnapshot = current,
                snapshot = SessionSnapshot(state = SessionState.OFF),
                accepted = false,
                reason = "feature_disabled"
            )
        }

        var snapshot = if (current.state == SessionState.OFF) {
            current.copy(state = SessionState.IDLE)
        } else {
            current
        }

        if (snapshot.state == SessionState.WALKING) {
            if (snapshot.lastEvidenceMs <= 0L ||
                nowMs - snapshot.lastEvidenceMs > config.sessionMaxEvidenceMs
            ) {
                return SessionUpdate(
                    previousSnapshot = current,
                    snapshot = SessionSnapshot(
                        state = SessionState.IDLE,
                        lastEventElapsedNanos = snapshot.lastEventElapsedNanos
                    ),
                    accepted = true,
                    reason = "session_expired"
                )
            }

            if (snapshot.pendingStopSinceMs > 0L &&
                nowMs - snapshot.pendingStopSinceMs >= config.stopConfirmationMs
            ) {
                return SessionUpdate(
                    previousSnapshot = current,
                    snapshot = SessionSnapshot(
                        state = SessionState.IDLE,
                        lastEventElapsedNanos = snapshot.lastEventElapsedNanos
                    ),
                    accepted = true,
                    reason = "walking_stopped"
                )
            }
        } else if (snapshot.pendingEnterSinceMs > 0L) {
            val age = nowMs - snapshot.pendingEnterSinceMs
            if (age >= config.walkingConfirmationMs) {
                return if (age > config.pendingEnterTimeoutMs) {
                    SessionUpdate(
                        previousSnapshot = current,
                        snapshot = snapshot.copy(
                            pendingEnterSinceMs = 0L,
                            lastEvidenceMs = 0L
                        ),
                        accepted = true,
                        reason = "confirmation_timeout"
                    )
                } else {
                    SessionUpdate(
                        previousSnapshot = current,
                        snapshot = snapshot.copy(
                            state = SessionState.WALKING,
                            pendingEnterSinceMs = 0L,
                            pendingStopSinceMs = 0L,
                            walkingSinceMs = nowMs,
                            // Keep the timestamp of the last real walking evidence.
                            // A delayed confirmation must not fabricate fresh evidence.
                            lastEvidenceMs = snapshot.lastEvidenceMs
                        ),
                        accepted = true,
                        reason = "walking_confirmed"
                    )
                }
            }
        }

        return SessionUpdate(
            previousSnapshot = current,
            snapshot = snapshot,
            accepted = false,
            reason = "tick_no_change"
        )
    }

    /**
     * Delay until the next useful check, or null when the feature is idle and no alarm is
     * needed. The caller schedules an alarm for that delay.
     */
    fun nextTickDelayMs(
        snapshot: SessionSnapshot,
        nowMs: Long,
        enabled: Boolean
    ): Long? {
        if (!enabled || snapshot.state == SessionState.OFF) return null

        if (snapshot.pendingStopSinceMs > 0L) {
            return (snapshot.pendingStopSinceMs + config.stopConfirmationMs - nowMs)
                .coerceAtLeast(0L)
        }

        if (snapshot.state == SessionState.WALKING) {
            if (snapshot.lastEvidenceMs <= 0L) return null
            val remaining =
                snapshot.lastEvidenceMs + config.sessionMaxEvidenceMs - nowMs
            return if (remaining <= 0L) 0L
            else minOf(config.recheckIntervalMs, remaining)
        }

        if (snapshot.pendingEnterSinceMs > 0L) {
            return (snapshot.pendingEnterSinceMs + config.walkingConfirmationMs - nowMs)
                .coerceAtLeast(0L)
        }

        return null
    }

    private fun expireIfStale(
        snapshot: SessionSnapshot,
        nowMs: Long
    ): SessionSnapshot {
        if (snapshot.state != SessionState.WALKING) return snapshot
        if (snapshot.lastEvidenceMs > 0L &&
            nowMs - snapshot.lastEvidenceMs <= config.sessionMaxEvidenceMs
        ) {
            return snapshot
        }
        return SessionSnapshot(
            state = SessionState.IDLE,
            lastEventElapsedNanos = snapshot.lastEventElapsedNanos
        )
    }
}
