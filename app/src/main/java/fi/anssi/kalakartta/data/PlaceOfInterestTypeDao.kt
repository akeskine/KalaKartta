package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaceOfInterestTypeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(type: PlaceOfInterestType)

    @Query("SELECT * FROM PlaceOfInterestType ORDER BY sortOrder")
    fun getAll(): List<PlaceOfInterestType>

    @Query("SELECT * FROM PlaceOfInterestType WHERE id = :id")
    fun getById(id: String): PlaceOfInterestType?
}
