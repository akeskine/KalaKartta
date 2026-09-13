package fi.anssi.kalakartta.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.cos

@RunWith(AndroidJUnit4::class)
class TrackPointFilterDaoTest {

    private lateinit var database: AppDatabase
    private var firstSessionId = 0L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        firstSessionId = database.fishingSessionDao().insert(
            FishingSession(startedAt = 1_000L)
        )
        val secondSessionId = database.fishingSessionDao().insert(
            FishingSession(startedAt = 3_000L)
        )

        database.trackPointDao().insertAll(
            listOf(
                TrackPoint(fishingSessionId = firstSessionId, timestamp = 1_000L, latitude = 60.0, longitude = 25.0, speed = 1.0f, accuracy = 3.0f),
                TrackPoint(fishingSessionId = firstSessionId, timestamp = 2_000L, latitude = 60.01, longitude = 25.0, speed = 2.0f, accuracy = 3.0f),
                TrackPoint(fishingSessionId = secondSessionId, timestamp = 3_000L, latitude = 60.02, longitude = 25.0, speed = 3.0f, accuracy = 3.0f)
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun routeCountAppliesTimeAreaAndSpeedFilters() {
        val dao = database.trackPointDao()

        assertEquals(3, dao.getCountFilteredForRoutes(
            minSessionStart = 0L,
            checkRange = false,
            startDate = 0L,
            endDate = 0L,
            checkArea = false,
            latSouth = 0.0,
            latNorth = 0.0,
            lonWest = 0.0,
            lonEast = 0.0,
            removeTransitions = false,
            maxSpeed = 0.0f
        ))
        assertEquals(1, dao.getCountFilteredForRoutes(
            minSessionStart = 0L,
            checkRange = true,
            startDate = 1_500L,
            endDate = 2_500L,
            checkArea = false,
            latSouth = 0.0,
            latNorth = 0.0,
            lonWest = 0.0,
            lonEast = 0.0,
            removeTransitions = false,
            maxSpeed = 0.0f
        ))
        assertEquals(1, dao.getCountFilteredForRoutes(
            minSessionStart = 0L,
            checkRange = false,
            startDate = 0L,
            endDate = 0L,
            checkArea = true,
            latSouth = 60.005,
            latNorth = 60.015,
            lonWest = 24.99,
            lonEast = 25.01,
            removeTransitions = false,
            maxSpeed = 0.0f
        ))
        assertEquals(2, dao.getCountFilteredForRoutes(
            minSessionStart = 0L,
            checkRange = false,
            startDate = 0L,
            endDate = 0L,
            checkArea = false,
            latSouth = 0.0,
            latNorth = 0.0,
            lonWest = 0.0,
            lonEast = 0.0,
            removeTransitions = true,
            maxSpeed = 2.0f
        ))
    }

    @Test
    fun heatmapCellCountAppliesTimeAreaAndSpeedFilters() {
        val dao = database.trackPointDao()
        val latDegreeMeters = 111_320.0
        val lonDegreeMeters = latDegreeMeters * cos(Math.toRadians(60.0))

        fun count(
            checkRange: Boolean = false,
            startDate: Long = 0L,
            endDate: Long = 0L,
            checkArea: Boolean = false,
            latSouth: Double = 0.0,
            latNorth: Double = 0.0,
            lonWest: Double = 0.0,
            lonEast: Double = 0.0,
            removeTransitions: Boolean = false,
            maxSpeed: Float = 0.0f
        ) = dao.getHeatmapCellCountFiltered(
            checkRange = checkRange,
            startDate = startDate,
            endDate = endDate,
            checkArea = checkArea,
            latSouth = latSouth,
            latNorth = latNorth,
            lonWest = lonWest,
            lonEast = lonEast,
            removeTransitions = removeTransitions,
            maxSpeed = maxSpeed,
            latDegreeMeters = latDegreeMeters,
            lonDegreeMeters = lonDegreeMeters,
            gridSizeMeters = 1_000.0
        )

        assertEquals(3, count())
        assertEquals(1, count(checkRange = true, startDate = 1_500L, endDate = 2_500L))
        assertEquals(
            1,
            count(checkArea = true, latSouth = 60.005, latNorth = 60.015, lonWest = 24.99, lonEast = 25.01)
        )
        assertEquals(2, count(removeTransitions = true, maxSpeed = 2.0f))
    }
}
