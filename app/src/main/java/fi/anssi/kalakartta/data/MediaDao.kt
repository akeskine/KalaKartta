package fi.anssi.kalakartta.data

import androidx.room.*

@Dao
interface MediaDao {
    @Query("""
        SELECT * FROM Media 
        WHERE latitude BETWEEN :lat - 0.00005 AND :lat + 0.00005 
        AND longitude BETWEEN :lon - 0.00005 AND :lon + 0.00005 
        AND (
            (:time IS NULL AND pointTime IS NULL) OR 
            (pointTime BETWEEN :time - 1000 AND :time + 1000)
        )
    """)
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

    @Query("SELECT COUNT(*) FROM Media")
    fun getCount(): Int
}
