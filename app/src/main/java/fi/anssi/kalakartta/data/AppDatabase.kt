package fi.anssi.kalakartta.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [FishCatch::class, FishSpecies::class, WeatherError::class, PlaceOfInterest::class, PlaceOfInterestType::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fishCatchDao(): FishCatchDao
    abstract fun fishSpeciesDao(): FishSpeciesDao
    abstract fun weatherErrorDao(): WeatherErrorDao
    abstract fun placeOfInterestDao(): PlaceOfInterestDao
    abstract fun placeOfInterestTypeDao(): PlaceOfInterestTypeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kalakartta-db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishCatch ADD COLUMN tripNotes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `WeatherError` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `message` TEXT NOT NULL, `catchId` INTEGER)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishCatch ADD COLUMN weatherDataCompleteTime INTEGER")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `PlaceOfInterestType` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `PlaceOfInterest` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `typeId` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `name` TEXT NOT NULL, `additionalInfo` TEXT NOT NULL, `originalRef` TEXT NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PlaceOfInterestType ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishCatch ADD COLUMN eventType TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // caughtAt muutetaan pakollisesta valinnaiseksi
                // SQLite ei tue suoraan NOT NULL poistamista, joten Room hoitaa tämän yleensä taulun uudelleenluonnilla migraatiossa jos mahdollista,
                // mutta tässä tapauksessa yksinkertaisin tapa (koska SQLite) on antaa sen olla sellaisenaan ja vain sallia null-arvot koodissa jos mahdollista,
                // TAI tehdä perinteinen SQLite migraatio: luo uusi taulu, kopioi tiedot, poista vanha, nimeä uusi.
                
                db.execSQL("CREATE TABLE FishCatch_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "species TEXT NOT NULL, " +
                        "eventType TEXT, " +
                        "latitude REAL NOT NULL, " +
                        "longitude REAL NOT NULL, " +
                        "caughtAt INTEGER, " +
                        "weight INTEGER, " +
                        "length INTEGER, " +
                        "method TEXT NOT NULL, " +
                        "strikeDepth REAL, " +
                        "waterDepth REAL, " +
                        "waterTemp REAL, " +
                        "airTemp REAL, " +
                        "cloudiness INTEGER, " +
                        "rain INTEGER, " +
                        "rainHourMm REAL, " +
                        "windSpeed REAL, " +
                        "windDirection INTEGER, " +
                        "pressure REAL, " +
                        "weatherSource TEXT NOT NULL, " +
                        "weatherTime INTEGER, " +
                        "weatherStation TEXT NOT NULL, " +
                        "additionalInfo TEXT NOT NULL, " +
                        "originalRef TEXT NOT NULL, " +
                        "tripNotes TEXT NOT NULL, " +
                        "weatherDataCompleteTime INTEGER)")
                
                val columns = "id, species, eventType, latitude, longitude, caughtAt, weight, length, method, strikeDepth, waterDepth, waterTemp, airTemp, cloudiness, rain, rainHourMm, windSpeed, windDirection, pressure, weatherSource, weatherTime, weatherStation, additionalInfo, originalRef, tripNotes, weatherDataCompleteTime"
                db.execSQL("INSERT INTO FishCatch_new ($columns) SELECT $columns FROM FishCatch")
                db.execSQL("DROP TABLE FishCatch")
                db.execSQL("ALTER TABLE FishCatch_new RENAME TO FishCatch")
            }
        }
    }
}