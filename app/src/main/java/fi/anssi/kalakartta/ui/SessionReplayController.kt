package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.TrackPoint

/**
 * Owns the state and deterministic time stepping of fishing-session replay.
 * UI and map rendering stay outside this class.
 */
class SessionReplayController {
    data class Frame(
        val currentTime: Long,
        val visiblePoints: List<TrackPoint>
    )

    var points: List<TrackPoint> = emptyList()
        private set
    var startTime: Long = 0L
        private set
    var endTime: Long = 0L
        private set
    var currentTime: Long = 0L
        private set
    var speed: Int = DEFAULT_SPEED
        private set
    var isPlaying: Boolean = false
        private set
    private var speedAfterPlayback: Int? = null

    val restoresSpeedAfterPlayback: Boolean
        get() = speedAfterPlayback != null

    fun load(
        points: List<TrackPoint>,
        startTime: Long,
        endTime: Long,
        currentTime: Long = startTime,
        speed: Int = this.speed,
        isPlaying: Boolean = false,
        restoreSpeedAfterPlayback: Boolean = false
    ) {
        this.points = points.sortedBy { it.timestamp }
        this.startTime = startTime
        this.endTime = endTime.coerceAtLeast(startTime)
        this.currentTime = currentTime.coerceIn(this.startTime, this.endTime)
        this.speed = speed.coerceAtLeast(1)
        speedAfterPlayback = DEFAULT_SPEED.takeIf { restoreSpeedAfterPlayback }
        this.isPlaying = isPlaying && this.points.isNotEmpty() && this.currentTime < this.endTime
        if (restoreSpeedAfterPlayback && !this.isPlaying) restorePlaybackSpeed()
    }

    fun restorePlaybackState(
        currentTime: Long,
        speed: Int,
        isPlaying: Boolean,
        restoreSpeedAfterPlayback: Boolean = false
    ) {
        this.currentTime = currentTime
        this.speed = speed.coerceAtLeast(1)
        speedAfterPlayback = DEFAULT_SPEED.takeIf { restoreSpeedAfterPlayback }
        this.isPlaying = isPlaying
    }

    fun setPlaying(playing: Boolean) {
        if (!playing) restorePlaybackSpeed()
        isPlaying = playing && points.isNotEmpty() && currentTime < endTime
    }

    fun setSpeed(speed: Int) {
        this.speed = speed.coerceAtLeast(1)
        speedAfterPlayback = null
    }

    fun playAtSpeedUntilEnd(speed: Int, restoreSpeed: Int = DEFAULT_SPEED) {
        this.speed = speed.coerceAtLeast(1)
        speedAfterPlayback = restoreSpeed.coerceAtLeast(1)
        isPlaying = points.isNotEmpty() && currentTime < endTime
        if (!isPlaying) restorePlaybackSpeed()
    }

    fun seek(time: Long): Frame {
        currentTime = time.coerceIn(startTime, endTime)
        return frame()
    }

    /** Advances one real-time tick and returns the map frame to render. */
    fun advance(stepMillis: Long = DEFAULT_STEP_MILLIS): Frame {
        if (isPlaying && currentTime < endTime) {
            val simulationStep = stepMillis.coerceAtLeast(0L) * speed
            currentTime = (currentTime + simulationStep).coerceAtMost(endTime)
            if (currentTime >= endTime) {
                isPlaying = false
                restorePlaybackSpeed()
            }
        }
        return frame()
    }

    fun frame(): Frame {
        return Frame(
            currentTime = currentTime,
            visiblePoints = points.filter { it.timestamp <= currentTime }
        )
    }

    fun clear() {
        points = emptyList()
        startTime = 0L
        endTime = 0L
        currentTime = 0L
        speed = DEFAULT_SPEED
        isPlaying = false
        speedAfterPlayback = null
    }

    private fun restorePlaybackSpeed() {
        speedAfterPlayback?.let { speed = it }
        speedAfterPlayback = null
    }

    companion object {
        const val DEFAULT_SPEED = 360
        const val DEFAULT_STEP_MILLIS = 100L
        const val AUTO_PLAY_DURATION_MILLIS = 2_000L

        fun speedForPlaybackDuration(durationMillis: Long, playbackDurationMillis: Long = AUTO_PLAY_DURATION_MILLIS): Int {
            if (durationMillis <= 0L || playbackDurationMillis <= 0L) return DEFAULT_SPEED

            val speed = durationMillis / playbackDurationMillis +
                    if (durationMillis % playbackDurationMillis == 0L) 0L else 1L
            return speed.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
        }
    }
}
