package fi.anssi.kalakartta.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishingSessionMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE FishingSession (id INTEGER PRIMARY KEY NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, notes TEXT NOT NULL DEFAULT '', fisherman TEXT NOT NULL DEFAULT '')")
                        db.execSQL("CREATE TABLE TrackPoint (id INTEGER PRIMARY KEY NOT NULL, fishingSessionId INTEGER NOT NULL, timestamp INTEGER NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, speed REAL NOT NULL, accuracy REAL NOT NULL)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    }
                })
                .build()
        )
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrationClosesOlderOpenSessionsAndKeepsNewestForRecovery() {
        val database = helper.writableDatabase
        database.execSQL("INSERT INTO FishingSession (id, startedAt) VALUES (1, 100)")
        database.execSQL("INSERT INTO FishingSession (id, startedAt) VALUES (2, 200)")
        database.execSQL("INSERT INTO TrackPoint (id, fishingSessionId, timestamp, latitude, longitude, speed, accuracy) VALUES (1, 1, 150, 60.0, 25.0, 0.0, 3.0)")

        AppDatabase.MIGRATION_23_24.migrate(database)

        val sessions = database.query("SELECT id, endedAt FROM FishingSession ORDER BY id")
        sessions.moveToFirst()
        assertEquals(1L, sessions.getLong(0))
        assertEquals(150L, sessions.getLong(1))
        sessions.moveToNext()
        assertEquals(2L, sessions.getLong(0))
        assertNull(sessions.getString(1))
        sessions.close()

        val activeState = database.query("SELECT COUNT(*) FROM ActiveFishingSession")
        activeState.moveToFirst()
        assertEquals(0, activeState.getInt(0))
        activeState.close()
    }
}
