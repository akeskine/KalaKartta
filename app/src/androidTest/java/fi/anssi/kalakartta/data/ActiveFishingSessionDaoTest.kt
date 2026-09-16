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
class ActiveFishingSessionDaoTest {
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
    fun activeStateIsPersistedAndUpdatedAsOneSingleton() {
        val sessionId = database.fishingSessionDao().insert(FishingSession(startedAt = 1_000L))
        val dao = database.activeFishingSessionDao()
        val state = ActiveFishingSession(
            sessionId = sessionId,
            locationCheckIntervalSeconds = 10,
            minTrackPointIntervalSeconds = 30,
            maxTrackPointIntervalSeconds = 300,
            minTrackPointDistanceMeters = 20
        )

        dao.insert(state)
        assertEquals(state, dao.get())

        val updated = state.copy(minTrackPointIntervalSeconds = 60)
        dao.update(updated)
        assertEquals(updated, dao.get())
    }

    @Test
    fun unfinishedSessionsAreReturnedNewestFirst() {
        val older = database.fishingSessionDao().insert(FishingSession(startedAt = 1_000L))
        val newer = database.fishingSessionDao().insert(FishingSession(startedAt = 2_000L))

        assertEquals(
            listOf(newer, older),
            database.fishingSessionDao().getUnfinishedSessions().map { it.id }
        )
    }
}
