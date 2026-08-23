package fi.anssi.kalakartta.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [FishCatch::class, FishSpecies::class, WeatherError::class, PlaceOfInterest::class, PlaceOfInterestType::class, FishingSession::class, TrackPoint::class, Media::class],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fishCatchDao(): FishCatchDao
    abstract fun fishSpeciesDao(): FishSpeciesDao
    abstract fun weatherErrorDao(): WeatherErrorDao
    abstract fun placeOfInterestDao(): PlaceOfInterestDao
    abstract fun placeOfInterestTypeDao(): PlaceOfInterestTypeDao
    abstract fun fishingSessionDao(): FishingSessionDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun mediaDao(): MediaDao
    
    fun initializeDefaults() {
        // Esitäyttö
        val speciesDao = fishSpeciesDao()
        val defaults = FishSpecies.getDefaultList()

        defaults.forEach { species ->
            val existing = speciesDao.getById(species.id)
            if (existing == null) {
                speciesDao.insert(species)
            } else {
                var updated = false
                var toUpdate = existing

                // Päivitetään oletusikonit jos ne puuttuvat
                if (existing.icon_default.isEmpty() && species.icon_default.isNotEmpty()) {
                    toUpdate = toUpdate.copy(icon_default = species.icon_default)
                    updated = true
                }

                // Päivitetään paino- ja pituusrajat jos ne ovat 0 (eli ei vielä asetettu)
                if (existing.small_weight == 0L && species.small_weight != 0L) {
                    toUpdate = toUpdate.copy(
                        small_weight = species.small_weight,
                        small_length = species.small_length,
                        large_weight = species.large_weight,
                        large_length = species.large_length,
                        giant_weight = species.giant_weight,
                        giant_length = species.giant_length
                    )
                    updated = true
                }

                // Päivitetään järjestys jos se on 0 (eli uusi kenttä tai ei asetettu)
                if (existing.sortOrder == 0 && species.sortOrder != 0) {
                    toUpdate = toUpdate.copy(sortOrder = species.sortOrder)
                    updated = true
                }

                if (updated) {
                    speciesDao.insert(toUpdate)
                }
            }
        }

        // Muut paikat (PlaceOfInterestType) esitäyttö
        val placeTypeDao = placeOfInterestTypeDao()
        val placeDefaults = PlaceOfInterestType.getDefaultList()

        placeDefaults.forEach { type ->
            val existing = placeTypeDao.getById(type.id)
            if (existing == null) {
                placeTypeDao.insert(type)
            } else {
                var updated = false
                var toUpdate = existing
                if (existing.icon.isEmpty() && type.icon.isNotEmpty()) {
                    toUpdate = toUpdate.copy(icon = type.icon)
                    updated = true
                }
                if (existing.sortOrder != type.sortOrder) {
                    toUpdate = toUpdate.copy(sortOrder = type.sortOrder)
                    updated = true
                }
                if (updated) {
                    placeTypeDao.insert(toUpdate)
                }
            }
        }
    }

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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .allowMainThreadQueries()
                .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishingSession ADD COLUMN fisherman TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `Media` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `pointTime` INTEGER, `mimeType` TEXT NOT NULL, `originalFileName` TEXT NOT NULL, `fileName` TEXT NOT NULL, `externalId` TEXT)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishCatch ADD COLUMN lure TEXT")
                db.execSQL("ALTER TABLE FishCatch ADD COLUMN lureColor TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishingSession ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `FishingSession` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `TrackPoint` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fishingSessionId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speed` REAL NOT NULL, `accuracy` REAL NOT NULL, FOREIGN KEY(`fishingSessionId`) REFERENCES `FishingSession`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_TrackPoint_fishingSessionId` ON `TrackPoint` (`fishingSessionId`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishSpecies ADD COLUMN favourite_fish INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE FishSpecies ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishCatch ADD COLUMN otherSpecies TEXT")
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
                // Lisätään mahdollisesti puuttuvat sarakkeet, jotka on voitu lisätä koodiin ilman migraatiota
                // aiemmissa kehitysversioissa.
                val missingColumns = listOf(
                    "strikeDepth REAL",
                    "waterDepth REAL",
                    "waterTemp REAL",
                    "airTemp REAL",
                    "cloudiness INTEGER",
                    "rain INTEGER",
                    "rainHourMm REAL",
                    "windSpeed REAL",
                    "windDirection INTEGER",
                    "pressure REAL",
                    "weatherSource TEXT NOT NULL DEFAULT ''",
                    "weatherTime INTEGER",
                    "weatherStation TEXT NOT NULL DEFAULT ''",
                    "additionalInfo TEXT NOT NULL DEFAULT ''",
                    "originalRef TEXT NOT NULL DEFAULT ''"
                )

                for (columnDef in missingColumns) {
                    val columnName = columnDef.split(" ")[0]
                    val cursor = db.query("PRAGMA table_info(FishCatch)")
                    var exists = false
                    while (cursor.moveToNext()) {
                        val nameIndex = cursor.getColumnIndex("name")
                        if (nameIndex != -1 && cursor.getString(nameIndex) == columnName) {
                            exists = true
                            break
                        }
                    }
                    cursor.close()
                    
                    if (!exists) {
                        db.execSQL("ALTER TABLE FishCatch ADD COLUMN $columnDef")
                    }
                }

                // caughtAt muutetaan pakollisesta valinnaiseksi
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

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE FishCatch ADD COLUMN fisherman TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}