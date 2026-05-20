package fi.anssi.kalakartta.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FishCatch::class, FishSpecies::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fishCatchDao(): FishCatchDao
    abstract fun fishSpeciesDao(): FishSpeciesDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE FishCatch ADD COLUMN tripNotes TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}