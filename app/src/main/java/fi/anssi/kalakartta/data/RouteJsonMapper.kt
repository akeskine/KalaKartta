package fi.anssi.kalakartta.data

import android.util.JsonReader
import android.util.JsonWriter
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date

/** FishingSession- ja TrackPoint-mallien streaming-JSON-muunnos. */
class RouteJsonMapper(
    private val isoFormatProvider: () -> SimpleDateFormat
) {

    fun writeRoutes(
        writer: JsonWriter,
        sessions: List<FishingSession>,
        onGetPoints: (Long) -> List<TrackPoint>
    ) {
        writer.beginObject()
        writer.name("sessions")
        writer.beginArray()

        sessions.forEach { session ->
            writer.beginObject()
            writer.name("startedAt").value(isoFormatProvider().format(Date(session.startedAt)))
            if (session.endedAt != null) {
                writer.name("endedAt").value(isoFormatProvider().format(Date(session.endedAt)))
            }
            writer.name("notes").value(session.notes)
            writer.name("fisherman").value(session.fisherman.uppercase())
            writer.name("points")
            writer.beginArray()
            onGetPoints(session.id).forEach { point ->
                writer.beginObject()
                writer.name("timestamp").value(isoFormatProvider().format(Date(point.timestamp)))
                writer.name("latitude").value(point.latitude)
                writer.name("longitude").value(point.longitude)
                writer.name("speed").value(point.speed.toDouble())
                writer.name("accuracy").value(point.accuracy.toDouble())
                writer.endObject()
            }
            writer.endArray()
            writer.endObject()
        }

        writer.endArray()
        writer.endObject()
    }

    fun readRoutes(
        inputStream: InputStream,
        onSessionParsed: (FishingSession, List<TrackPoint>) -> Unit
    ) {
        val reader = inputStream.bufferedReader()
        val jsonReader = JsonReader(reader)
        try {
            jsonReader.beginObject()
            while (jsonReader.hasNext()) {
                val name = jsonReader.nextName()
                if (name == "sessions") {
                    jsonReader.beginArray()
                    while (jsonReader.hasNext()) {
                        parseSession(jsonReader, onSessionParsed)
                    }
                    jsonReader.endArray()
                } else {
                    jsonReader.skipValue()
                }
            }
            jsonReader.endObject()
        } finally {
            // Virtaa ei suljeta tässä: se voi olla ZipInputStreamin sisäinen virta.
        }
    }

    private fun parseSession(
        reader: JsonReader,
        onSessionParsed: (FishingSession, List<TrackPoint>) -> Unit
    ) {
        var startedAtMs = 0L
        var endedAtMs: Long? = null
        var notes = ""
        var fisherman = ""
        var pointsFound = false

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "startedAt" -> startedAtMs = parseDate(reader.nextString()) ?: 0L
                "endedAt" -> {
                    val endedAt = reader.nextString()
                    endedAtMs = if (endedAt.isNotEmpty()) parseDate(endedAt) else null
                }
                "notes" -> notes = reader.nextString()
                "fisherman" -> fisherman = reader.nextString()
                "points" -> {
                    pointsFound = true
                    val pointsBatch = mutableListOf<TrackPoint>()

                    reader.beginArray()
                    while (reader.hasNext()) {
                        pointsBatch += parseTrackPoint(reader)
                        if (pointsBatch.size >= 1000) {
                            onSessionParsed(RouteDomainMapper.sessionForPoints(pointsBatch, notes, fisherman), pointsBatch.toList())
                            pointsBatch.clear()
                        }
                    }
                    reader.endArray()

                    if (pointsBatch.isNotEmpty() || !pointsFound) {
                        val session = if (pointsBatch.isNotEmpty()) {
                            RouteDomainMapper.sessionForPoints(pointsBatch, notes, fisherman)
                        } else {
                            RouteDomainMapper.session(startedAtMs, endedAtMs, notes, fisherman)
                        }
                        onSessionParsed(session, pointsBatch)
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (!pointsFound) {
            onSessionParsed(RouteDomainMapper.session(startedAtMs, endedAtMs, notes, fisherman), emptyList())
        }
    }

    private fun parseTrackPoint(reader: JsonReader): TrackPoint {
        var timestampMs = 0L
        var latitude = 0.0
        var longitude = 0.0
        var speed = 0.0f
        var accuracy = 0.0f

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "timestamp" -> timestampMs = parseDate(reader.nextString()) ?: 0L
                "latitude" -> latitude = reader.nextDouble()
                "longitude" -> longitude = reader.nextDouble()
                "speed" -> speed = reader.nextDouble().toFloat()
                "accuracy" -> accuracy = reader.nextDouble().toFloat()
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return RouteDomainMapper.point(timestampMs, latitude, longitude, speed, accuracy)
    }

    private fun parseDate(value: String): Long? = try {
        isoFormatProvider().parse(value)?.time
    } catch (_: Exception) {
        null
    }
}
