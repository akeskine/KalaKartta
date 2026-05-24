package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WeatherErrorDao {
    @Query("SELECT * FROM WeatherError ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getErrors(limit: Int, offset: Int): List<WeatherError>

    @Insert
    fun insert(error: WeatherError)

    @Query("DELETE FROM WeatherError")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM WeatherError")
    fun getCount(): Int
}
