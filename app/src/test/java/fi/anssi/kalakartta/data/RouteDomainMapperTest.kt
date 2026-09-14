package fi.anssi.kalakartta.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDomainMapperTest {

    @Test
    fun mapsSessionMetadataWithoutDatabaseId() {
        val session = RouteDomainMapper.session(1000L, 2000L, "Muistiinpano", "ANSSI")

        assertEquals(0L, session.id)
        assertEquals(1000L, session.startedAt)
        assertEquals(2000L, session.endedAt)
        assertEquals("Muistiinpano", session.notes)
        assertEquals("ANSSI", session.fisherman)
    }

    @Test
    fun mapsTrackPointWithoutDatabaseOrSessionId() {
        val point = RouteDomainMapper.point(3000L, 60.1, 24.2, 1.5f, 4.0f)

        assertEquals(0L, point.id)
        assertEquals(0L, point.fishingSessionId)
        assertEquals(3000L, point.timestamp)
        assertEquals(60.1, point.latitude, 0.000001)
        assertEquals(24.2, point.longitude, 0.000001)
        assertEquals(1.5f, point.speed, 0.000001f)
        assertEquals(4.0f, point.accuracy, 0.000001f)
    }

    @Test
    fun derivesSessionBoundsFromPointOrder() {
        val points = listOf(
            RouteDomainMapper.point(1000L, 60.0, 24.0, 0f, 0f),
            RouteDomainMapper.point(2000L, 60.1, 24.1, 0f, 0f)
        )

        val session = RouteDomainMapper.sessionForPoints(points, "", "")

        assertEquals(1000L, session.startedAt)
        assertEquals(2000L, session.endedAt)
    }
}
