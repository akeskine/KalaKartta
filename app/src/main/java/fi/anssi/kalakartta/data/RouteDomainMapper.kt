package fi.anssi.kalakartta.data

/** Reittitiedoston scalar-arvojen muunnos sovelluksen domain-malleiksi. */
object RouteDomainMapper {

    fun session(
        startedAt: Long,
        endedAt: Long?,
        notes: String,
        fisherman: String
    ): FishingSession = FishingSession(
        startedAt = startedAt,
        endedAt = endedAt,
        notes = notes,
        fisherman = fisherman
    )

    fun point(
        timestamp: Long,
        latitude: Double,
        longitude: Double,
        speed: Float,
        accuracy: Float
    ): TrackPoint = TrackPoint(
        fishingSessionId = 0,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        speed = speed,
        accuracy = accuracy
    )

    fun sessionForPoints(
        points: List<TrackPoint>,
        notes: String,
        fisherman: String
    ): FishingSession = session(
        startedAt = points.first().timestamp,
        endedAt = points.last().timestamp,
        notes = notes,
        fisherman = fisherman
    )
}
