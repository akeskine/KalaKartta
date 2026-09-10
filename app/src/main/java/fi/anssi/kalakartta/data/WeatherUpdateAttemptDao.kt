package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WeatherUpdateAttemptDao {
    @Query("SELECT * FROM WeatherUpdateAttempt")
    fun getAll(): List<WeatherUpdateAttempt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(attempt: WeatherUpdateAttempt)

    @Query("DELETE FROM WeatherUpdateAttempt")
    fun deleteAll()
}