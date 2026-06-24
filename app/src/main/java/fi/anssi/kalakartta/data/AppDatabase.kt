package fi.anssi.kalakartta.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [FishCatch::class, FishSpecies::class, WeatherError::class, PlaceOfInterest::class, PlaceOfInterestType::class],
    version = 6,
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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
    }
}