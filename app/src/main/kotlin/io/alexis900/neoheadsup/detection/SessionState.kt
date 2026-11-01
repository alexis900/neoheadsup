package io.alexis900.neoheadsup.detection

/** Feature state. Pending confirmations are represented as timestamps in [SessionSnapshot]. */
enum class SessionState {
    OFF,
    IDLE,
    WALKING
}
