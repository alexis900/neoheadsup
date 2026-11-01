package io.alexis900.neoheadsup.detection

/**
 * Persistable detection state.
 *
 * Timestamps are wall-clock milliseconds (System.currentTimeMillis) so they survive process
 * restarts. [lastEventElapsedNanos] comes from the activity transition event and is only
 * used to reject duplicated or out-of-order events while the device has not rebooted.
 */
data class SessionSnapshot(
    val state: SessionState = SessionState.IDLE,
    val pendingEnterSinceMs: Long = 0L,
    val pendingStopSinceMs: Long = 0L,
    val walkingSinceMs: Long = 0L,
    val lastEvidenceMs: Long = 0L,
    val lastEventElapsedNanos: Long = 0L
)
