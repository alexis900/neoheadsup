package io.alexis900.neoheadsup.detection

data class SessionUpdate(
    val previousSnapshot: SessionSnapshot,
    val snapshot: SessionSnapshot,
    val accepted: Boolean,
    val reason: String
) {
    val previousState: SessionState get() = previousSnapshot.state
    val newState: SessionState get() = snapshot.state
    val changed: Boolean get() = previousSnapshot != snapshot
}
