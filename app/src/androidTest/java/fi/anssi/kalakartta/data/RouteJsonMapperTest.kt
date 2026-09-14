package fi.anssi.kalakartta.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RouteJsonMapperTest {

    @Test
    fun keepsStreamingPointBatchesInOneSessionAndPreservesEmptySessions() {
        val json = buildString {
            append("{\"sessions\":[")
            append("{\"startedAt\":\"1970-01-01T00:00:01Z\",\"endedAt\":\"1970-01-01T00:00:02Z\",\"notes\":\"test\",\"fisherman\":\"Anssi\",\"points\":[")
            repeat(1001) { index ->
                if (index > 0) append(',')
                append("{\"timestamp\":\"1970-01-01T00:00:02Z\",\"latitude\":60.0,\"longitude\":24.0,\"speed\":0.0,\"accuracy\":1.0}")
            }
            append("]},")
            append("{\"startedAt\":\"1970-01-01T00:00:03Z\",\"endedAt\":\"1970-01-01T00:00:03Z\",\"notes\":\"empty\",\"fisherman\":\"Anssi\",\"points\":[]}")
            append("]}")
        }

        val callbacks = mutableListOf<Pair<FishingSession, List<TrackPoint>>>()
        val mapper = RouteJsonMapper {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }

        mapper.readRoutes(ByteArrayInputStream(json.toByteArray())) { session, points ->
            callbacks += session to points
        }

        assertEquals(3, callbacks.size)
        assertEquals(1000, callbacks[0].second.size)
        assertEquals(1, callbacks[1].second.size)
        assertEquals(callbacks[0].first.startedAt, callbacks[1].first.startedAt)
        assertEquals(1_000L, callbacks[0].first.startedAt)
        assertEquals(0, callbacks[2].second.size)
        assertEquals(3_000L, callbacks[2].first.startedAt)
    }
}
