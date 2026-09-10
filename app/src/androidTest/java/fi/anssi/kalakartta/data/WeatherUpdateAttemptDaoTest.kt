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
class WeatherUpdateAttemptDaoTest {
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
    fun upsertReplacesThePreviousAttemptForTheSameCatch() {
        val dao = database.weatherUpdateAttemptDao()
        dao.upsert(WeatherUpdateAttempt(42L, 100L, succeeded = false, errorMessage = "timeout"))
        val successfulAttempt = WeatherUpdateAttempt(42L, 200L, succeeded = true)

        dao.upsert(successfulAttempt)

        assertEquals(listOf(successfulAttempt), dao.getAll())
    }
}