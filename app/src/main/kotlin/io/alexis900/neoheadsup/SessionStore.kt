package io.alexis900.neoheadsup

import android.content.Context
import io.alexis900.neoheadsup.detection.SessionSnapshot
import io.alexis900.neoheadsup.detection.SessionState

/**
 * Persists the detection snapshot in a small private SharedPreferences file.
 *
 * The legacy [Prefs] walking_since key is deliberately removed: keeping a wall-clock
 * timestamp was the cause of reminders being generated from stale sessions after the
 * process or the device had been idle for a long time.
 */
object SessionStore {

    private const val FILE = "neoheadsup_session"
    private const val KEY_STATE = "state"
    private const val KEY_PENDING_ENTER = "pending_enter_ms"
    private const val KEY_PENDING_STOP = "pending_stop_ms"
    private const val KEY_WALKING_SINCE = "walking_since_ms"
    private const val KEY_LAST_EVIDENCE = "last_evidence_ms"
    private const val KEY_LAST_EVENT_NANOS = "last_event_elapsed_nanos"

    fun load(context: Context): SessionSnapshot {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val state = runCatching {
            SessionState.valueOf(
                prefs.getString(KEY_STATE, SessionState.IDLE.name)
                    ?: SessionState.IDLE.name
            )
        }.getOrDefault(SessionState.IDLE)

        return SessionSnapshot(
            state = state,
            pendingEnterSinceMs = prefs.getLong(KEY_PENDING_ENTER, 0L),
            pendingStopSinceMs = prefs.getLong(KEY_PENDING_STOP, 0L),
            walkingSinceMs = prefs.getLong(KEY_WALKING_SINCE, 0L),
            lastEvidenceMs = prefs.getLong(KEY_LAST_EVIDENCE, 0L),
            lastEventElapsedNanos = prefs.getLong(KEY_LAST_EVENT_NANOS, 0L)
        )
    }

    fun save(context: Context, snapshot: SessionSnapshot) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, snapshot.state.name)
            .putLong(KEY_PENDING_ENTER, snapshot.pendingEnterSinceMs)
            .putLong(KEY_PENDING_STOP, snapshot.pendingStopSinceMs)
            .putLong(KEY_WALKING_SINCE, snapshot.walkingSinceMs)
            .putLong(KEY_LAST_EVIDENCE, snapshot.lastEvidenceMs)
            .putLong(KEY_LAST_EVENT_NANOS, snapshot.lastEventElapsedNanos)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
