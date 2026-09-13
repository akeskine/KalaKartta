package fi.anssi.kalakartta.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseDataLifecycleTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initializeDefaultsSeedsExpectedReferenceData() {
        database.initializeDefaults()

        assertEquals(
            FishSpecies.getDefaultList(),
            database.fishSpeciesDao().getAll()
        )
        assertEquals(
            PlaceOfInterestType.getDefaultList(),
            database.placeOfInterestTypeDao().getAll()
        )
    }

    @Test
    fun clearAllTablesRemovesUserDataAndDefaultsCanBeRestored() {
        database.initializeDefaults()

        val sessionId = database.fishingSessionDao().insert(
            FishingSession(startedAt = 1_000L)
        )
        database.fishCatchDao().insert(
            FishCatch(
                species = "PERCH",
                latitude = 60.0,
                longitude = 25.0,
                caughtAt = 2_000L
            )
        )
        database.placeOfInterestDao().insert(
            PlaceOfInterest(
                typeId = "ROCK",
                latitude = 60.0,
                longitude = 25.0,
                name = "Test place"
            )
        )
        database.trackPointDao().insert(
            TrackPoint(
                fishingSessionId = sessionId,
                timestamp = 2_000L,
                latitude = 60.0,
                longitude = 25.0,
                speed = 1.0f,
                accuracy = 3.0f
            )
        )
        database.mediaDao().insert(
            Media(
                mimeType = "image/jpeg",
                originalFileName = "test.jpg",
                fileName = "test.jpg"
            )
        )
        database.fishDiaryPageDao().insert(
            FishDiaryPage(
                startDate = 2_000L,
                location = "Test location",
                fishingMethod = "Test method",
                catch = "Test catch",
                story = "Test story"
            )
        )
        database.weatherErrorDao().insert(
            WeatherError(timestamp = 2_000L, message = "Test error")
        )
        database.weatherUpdateAttemptDao().upsert(
            WeatherUpdateAttempt(
                catchId = 1L,
                attemptedAt = 2_000L,
                succeeded = false,
                errorMessage = "Test failure"
            )
        )

        database.clearAllTables()

        assertEquals(0, database.fishCatchDao().getCount())
        assertEquals(0, database.placeOfInterestDao().getCount())
        assertEquals(0, database.fishingSessionDao().getCount())
        assertEquals(0, database.trackPointDao().getCount())
        assertEquals(0, database.mediaDao().getCount())
        assertEquals(0, database.fishDiaryPageDao().getAll().size)
        assertEquals(0, database.weatherErrorDao().getCount())
        assertEquals(0, database.weatherUpdateAttemptDao().getAll().size)
        assertEquals(0, database.fishSpeciesDao().getAll().size)
        assertEquals(0, database.placeOfInterestTypeDao().getAll().size)

        database.initializeDefaults()

        assertEquals(FishSpecies.getDefaultList(), database.fishSpeciesDao().getAll())
        assertEquals(
            PlaceOfInterestType.getDefaultList(),
            database.placeOfInterestTypeDao().getAll()
        )
    }
}
