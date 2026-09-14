package fi.anssi.kalakartta.ui

import fi.anssi.kalakartta.data.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReplayControllerTest {
    private val firstPoint = TrackPoint(
        id = 1L,
        fishingSessionId = 7L,
        timestamp = 1_000L,
        latitude = 60.0,
        longitude = 24.0,
        speed = 0f,
        accuracy = 5f
    )
    private val secondPoint = firstPoint.copy(
        id = 2L,
        timestamp = 2_000L,
        latitude = 60.001
    )

    @Test
    fun `load sorts points and starts at session beginning`() {
        val controller = SessionReplayController()

        controller.load(
            points = listOf(secondPoint, firstPoint),
            startTime = 1_000L,
            endTime = 2_000L
        )

        assertEquals(listOf(firstPoint, secondPoint), controller.points)
        assertEquals(1_000L, controller.currentTime)
        assertEquals(listOf(firstPoint), controller.frame().visiblePoints)
        assertFalse(controller.isPlaying)
    }

    @Test
    fun `advance follows speed and stops at end`() {
        val controller = SessionReplayController()
        controller.load(listOf(firstPoint, secondPoint), 1_000L, 2_000L, speed = 10)
        controller.setPlaying(true)

        val frame = controller.advance(stepMillis = 100L)

        assertEquals(2_000L, frame.currentTime)
        assertEquals(listOf(firstPoint, secondPoint), frame.visiblePoints)
        assertFalse(controller.isPlaying)
    }

    @Test
    fun `load at session end shows the complete route and is paused`() {
        val controller = SessionReplayController()

        controller.load(
            points = listOf(firstPoint, secondPoint),
            startTime = 1_000L,
            endTime = 2_000L,
            currentTime = 2_000L
        )

        assertEquals(2_000L, controller.currentTime)
        assertEquals(listOf(firstPoint, secondPoint), controller.frame().visiblePoints)
        assertFalse(controller.isPlaying)
    }

    @Test
    fun `seek clamps to session bounds`() {
        val controller = SessionReplayController()
        controller.load(listOf(firstPoint, secondPoint), 1_000L, 2_000L)

        controller.seek(9_000L)
        assertEquals(2_000L, controller.currentTime)
        assertTrue(controller.frame().visiblePoints.contains(secondPoint))

        controller.seek(0L)
        assertEquals(1_000L, controller.currentTime)
    }

    @Test
    fun `clear stops playback and removes replay state`() {
        val controller = SessionReplayController()
        controller.load(
            points = listOf(firstPoint, secondPoint),
            startTime = 1_000L,
            endTime = 2_000L,
            currentTime = 1_500L,
            isPlaying = true
        )

        controller.clear()

        assertFalse(controller.isPlaying)
        assertEquals(emptyList<TrackPoint>(), controller.points)
        assertEquals(0L, controller.currentTime)
        assertEquals(emptyList<TrackPoint>(), controller.frame().visiblePoints)
    }
}
