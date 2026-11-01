package io.alexis900.neoheadsup.detection

/**
 * Timing values for walking detection.
 *
 * The reminder should be conservative: a walking session is only accepted after continuous
 * evidence, a stop is only accepted after a short confirmation, and a session without fresh
 * evidence expires instead of being kept alive artificially.
 */
data class SessionConfig(
    /** Continuous WALKING evidence required before a session is confirmed. */
    val walkingConfirmationMs: Long = 15_000L,
    /** Continuous not-walking evidence required before a session is closed. */
    val stopConfirmationMs: Long = 10_000L,
    /** Maximum age of the last walking evidence before the session is discarded. */
    val sessionMaxEvidenceMs: Long = 10 * 60_000L,
    /** How often the screen is re-checked while walking. */
    val recheckIntervalMs: Long = 60_000L,
    /** If the confirmation tick is missed for this long, drop the pending start. */
    val pendingEnterTimeoutMs: Long = 10 * 60_000L
) {
    companion object {
        const val DEFAULT_COOLDOWN_MINUTES = 15
        const val SNOOZE_MINUTES = 30
    }
}
