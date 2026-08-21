package fi.anssi.kalakartta.data

import androidx.room.*

@Dao
interface MediaDao {
    @Query("SELECT * FROM Media WHERE latitude = :lat AND longitude = :lon AND (pointTime = :time OR (pointTime IS NULL AND :time IS NULL))")
    fun getMediaForPoint(lat: Double, lon: Double, time: Long?): List<Media>

    @Insert
    fun insert(media: Media): Long

    @Delete
    fun delete(media: Media)

    @Query("SELECT * FROM Media")
    fun getAll(): List<Media>

    @Query("DELETE FROM Media")
    fun deleteAll()

    @Query("SELECT * FROM Media WHERE externalId = :externalId")
    fun getByExternalId(externalId: String): Media?
}
