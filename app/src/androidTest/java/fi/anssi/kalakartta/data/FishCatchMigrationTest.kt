package fi.anssi.kalakartta.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FishCatchMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE FishCatch (id INTEGER PRIMARY KEY NOT NULL)")
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
    fun migrationAddsNullablePressureTurningTrendColumn() {
        val database = helper.writableDatabase

        AppDatabase.MIGRATION_22_23.migrate(database)

        val emptyCursor = database.query("SELECT pressureTurningTrend FROM FishCatch")
        assertTrue(emptyCursor.columnCount == 1)
        assertTrue(!emptyCursor.moveToFirst())
        emptyCursor.close()

        database.execSQL("INSERT INTO FishCatch (id) VALUES (1)")
        val cursor = database.query("SELECT pressureTurningTrend FROM FishCatch WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.isNull(0))
        cursor.close()
    }
}