package io.alexis900.neoheadsup.detection

enum class TransitionType {
    ENTER,
    EXIT
}

data class DetectionEvent(
    val transition: TransitionType,
    val activityType: Int,
    val wallClockMs: Long,
    val elapsedRealtimeNanos: Long
)
