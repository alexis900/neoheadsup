package io.alexis900.neoheadsup.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the pure detection core. They cover the nine scenarios requested for the
 * NeoHeadsUp false-positive fix without needing a device.
 */
class DetectionCoreTest {

    private val controller = SessionController()
    private val config = SessionConfig()

    private val walkingActivity = 8 // DetectedActivity.WALKING, kept literal for JVM tests
    private val start = 1_000_000L
    private val confirmationMs = config.walkingConfirmationMs
    private val stopMs = config.stopConfirmationMs

    // 1. Seated user with no previous activity.
    @Test
    fun seatedWithoutPreviousActivityNeverWalksOrAlerts() {
        val update = controller.onTick(SessionSnapshot(), start, enabled = true)

        assertEquals(SessionState.IDLE, update.snapshot.state)
        assertFalse(update.accepted)
        assertDenied(update.snapshot, start, "state_idle")
    }

    // 2. Seated user after having walked: the stop must be confirmed before the state ends.
    @Test
    fun seatedAfterWalkingClosesSessionAfterStopConfirmation() {
        val confirmed = confirmWalking(start)
        assertEquals(SessionState.WALKING, confirmed.state)

        val stopped = controller.onEvent(
            confirmed,
            event(TransitionType.EXIT, start + 20_000L, elapsed = 200L),
            enabled = true
        )
        assertEquals(SessionState.WALKING, stopped.snapshot.state)
        assertEquals(start + 20_000L, stopped.snapshot.pendingStopSinceMs)

        val stillWalking = controller.onTick(
            stopped.snapshot,
            start + 20_000L + stopMs - 1L,
            enabled = true
        )
        assertEquals(SessionState.WALKING, stillWalking.snapshot.state)

        val closed = controller.onTick(
            stillWalking.snapshot,
            start + 20_000L + stopMs,
            enabled = true
        )
        assertEquals(SessionState.IDLE, closed.snapshot.state)
        assertEquals("walking_stopped", closed.reason)
        assertDenied(closed.snapshot, start + 20_000L + stopMs, "state_idle")
    }

    // 3. Start of a walk requires continuous evidence for the whole confirmation window.
    @Test
    fun walkingStartsOnlyAfterConfirmationWindow() {
        val entered = controller.onEvent(
            SessionSnapshot(),
            event(TransitionType.ENTER, start, elapsed = 100L),
            enabled = true
        )
        assertEquals(SessionState.IDLE, entered.snapshot.state)
        assertEquals("walking_confirmation_started", entered.reason)

        val tooEarly = controller.onTick(
            entered.snapshot,
            start + confirmationMs - 1L,
            enabled = true
        )
        assertEquals(SessionState.IDLE, tooEarly.snapshot.state)
        assertEquals("tick_no_change", tooEarly.reason)

        val confirmed = controller.onTick(
            tooEarly.snapshot,
            start + confirmationMs,
            enabled = true
        )
        assertEquals(SessionState.WALKING, confirmed.snapshot.state)
        assertEquals("walking_confirmed", confirmed.reason)
        assertEquals(start, confirmed.snapshot.lastEvidenceMs)
        assertAllowed(confirmed.snapshot, start + confirmationMs)
    }

    // 4. End of a walk: a short EXIT is not enough, the stop needs 10 seconds.
    @Test
    fun walkingEndsOnlyAfterStopConfirmation() {
        val confirmed = confirmWalking(start)

        val exited = controller.onEvent(
            confirmed,
            event(TransitionType.EXIT, start + 30_000L, elapsed = 200L),
            enabled = true
        )
        assertEquals("stop_confirmation_started", exited.reason)

        val tooEarly = controller.onTick(
            exited.snapshot,
            start + 30_000L + stopMs - 1L,
            enabled = true
        )
        assertEquals(SessionState.WALKING, tooEarly.snapshot.state)

        val ended = controller.onTick(
            tooEarly.snapshot,
            start + 30_000L + stopMs,
            enabled = true
        )
        assertEquals(SessionState.IDLE, ended.snapshot.state)
        assertEquals("walking_stopped", ended.reason)
    }

    // 5. Duplicate events are rejected instead of refreshing the session.
    @Test
    fun duplicateEventsAreRejected() {
        val first = controller.onEvent(
            SessionSnapshot(),
            event(TransitionType.ENTER, start, elapsed = 100L),
            enabled = true
        )
        val duplicate = controller.onEvent(
            first.snapshot,
            event(TransitionType.ENTER, start + 500L, elapsed = 100L),
            enabled = true
        )

        assertFalse(duplicate.accepted)
        assertEquals("duplicate_event", duplicate.reason)
        assertEquals(first.snapshot, duplicate.snapshot)
    }

    // 6. Out-of-order events cannot move the state machine backwards or forwards.
    @Test
    fun outOfOrderEventsAreRejected() {
        val entered = controller.onEvent(
            SessionSnapshot(),
            event(TransitionType.ENTER, start, elapsed = 200L),
            enabled = true
        )
        val lateExit = controller.onEvent(
            entered.snapshot,
            event(TransitionType.EXIT, start + 1_000L, elapsed = 100L),
            enabled = true
        )

        assertFalse(lateExit.accepted)
        assertEquals("out_of_order", lateExit.reason)
        assertEquals(entered.snapshot, lateExit.snapshot)
        assertEquals(SessionState.IDLE, lateExit.snapshot.state)
    }

    // 7. Persisted states that no longer have recent evidence are discarded.
    @Test
    fun stalePersistedSessionIsDiscarded() {
        val stale = SessionSnapshot(
            state = SessionState.WALKING,
            walkingSinceMs = start - 60 * 60_000L,
            lastEvidenceMs = start - config.sessionMaxEvidenceMs - 1L,
            lastEventElapsedNanos = 100L
        )

        val update = controller.onTick(stale, start, enabled = true)

        assertEquals(SessionState.IDLE, update.snapshot.state)
        assertEquals("session_expired", update.reason)
        assertDenied(update.snapshot, start, "state_idle")
    }

    // 8. Process restart: a confirmed session is restored from storage and can continue.
    @Test
    fun processRestartRestoresConfirmedSessionButRebootClearsIt() {
        val stored = SessionSnapshot(
            state = SessionState.WALKING,
            walkingSinceMs = start,
            lastEvidenceMs = start + 15_000L,
            lastEventElapsedNanos = 100L
        )

        val restored = controller.onTick(
            stored,
            start + 15_000L + config.recheckIntervalMs,
            enabled = true
        )
        assertEquals(SessionState.WALKING, restored.snapshot.state)
        assertAllowed(restored.snapshot, start + 15_000L + config.recheckIntervalMs)

        val afterReboot = controller.onReboot(stored, enabled = true)
        assertEquals(SessionState.IDLE, afterReboot.snapshot.state)
        assertEquals(0L, afterReboot.snapshot.lastEvidenceMs)
        assertDenied(afterReboot.snapshot, start, "state_idle")
    }

    // 9. No alert can be produced without recent walking evidence.
    @Test
    fun alertsWithoutRecentEvidenceAreDenied() {
        val noEvidence = SessionSnapshot(state = SessionState.WALKING)
        assertDenied(noEvidence, start, "no_walking_evidence")

        val staleEvidence = SessionSnapshot(
            state = SessionState.WALKING,
            lastEvidenceMs = start - config.sessionMaxEvidenceMs - 1L
        )
        assertDenied(staleEvidence, start, "stale_evidence")

        val idle = SessionSnapshot(state = SessionState.IDLE)
        assertDenied(idle, start, "state_idle")
    }

    // Additional guards for the alert policy.
    @Test
    fun alertPolicyHonoursCooldownSnoozeLockAndDisabled() {
        val walking = walkingSnapshot(start)

        assertAllowed(walking, start)

        assertDenied(walking, start, "cooldown", lastNotificationMs = start - 1_000L)
        assertDenied(walking, start, "snoozed", snoozed = true)
        assertDenied(walking, start, "screen_off_or_locked", screenOn = false)
        assertDenied(walking, start, "disabled", enabled = false)
    }

    @Test
    fun rapidExitAndReturnDoesNotCloseOrDuplicateTheSession() {
        val confirmed = confirmWalking(start)

        val exited = controller.onEvent(
            confirmed,
            event(TransitionType.EXIT, start + 20_000L, elapsed = 200L),
            enabled = true
        )
        assertEquals(start + 20_000L, exited.snapshot.pendingStopSinceMs)

        val returned = controller.onEvent(
            exited.snapshot,
            event(TransitionType.ENTER, start + 25_000L, elapsed = 300L),
            enabled = true
        )
        assertEquals("stop_cancelled", returned.reason)
        assertEquals(0L, returned.snapshot.pendingStopSinceMs)
        assertEquals(SessionState.WALKING, returned.snapshot.state)
        assertAllowed(returned.snapshot, start + 25_000L)
    }

    private fun confirmWalking(start: Long): SessionSnapshot {
        val entered = controller.onEvent(
            SessionSnapshot(),
            event(TransitionType.ENTER, start, elapsed = 100L),
            enabled = true
        )
        return controller.onTick(
            entered.snapshot,
            start + confirmationMs,
            enabled = true
        ).snapshot
    }

    private fun walkingSnapshot(now: Long) = SessionSnapshot(
        state = SessionState.WALKING,
        walkingSinceMs = now - 1_000L,
        lastEvidenceMs = now - 1_000L,
        lastEventElapsedNanos = 100L
    )

    private fun event(
        transition: TransitionType,
        wallClockMs: Long,
        elapsed: Long
    ) = DetectionEvent(
        transition = transition,
        activityType = walkingActivity,
        wallClockMs = wallClockMs,
        elapsedRealtimeNanos = elapsed
    )

    private fun assertAllowed(snapshot: SessionSnapshot, now: Long) {
        val decision = AlertDecider.decide(snapshot, context(now))
        assertTrue(decision.reason, decision.allowed)
        assertEquals("allowed", decision.reason)
    }

    private fun assertDenied(
        snapshot: SessionSnapshot,
        now: Long,
        reason: String,
        lastNotificationMs: Long = 0L,
        snoozed: Boolean = false,
        screenOn: Boolean = true,
        enabled: Boolean = true
    ) {
        val decision = AlertDecider.decide(
            snapshot,
            context(
                now = now,
                lastNotificationMs = lastNotificationMs,
                snoozed = snoozed,
                screenOn = screenOn,
                enabled = enabled
            )
        )
        assertFalse(decision.allowed)
        assertEquals(reason, decision.reason)
    }

    private fun context(
        now: Long,
        lastNotificationMs: Long = 0L,
        snoozed: Boolean = false,
        screenOn: Boolean = true,
        enabled: Boolean = true
    ) = AlertContext(
        enabled = enabled,
        screenOnAndUnlocked = screenOn,
        snoozed = snoozed,
        cooldownMs = SessionConfig.DEFAULT_COOLDOWN_MINUTES * 60_000L,
        lastNotificationMs = lastNotificationMs,
        nowMs = now,
        config = config
    )
}
