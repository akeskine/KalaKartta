package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlaceOfInterestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(placeOfInterest: PlaceOfInterest): Long

    @Query("SELECT * FROM PlaceOfInterest")
    fun getAll(): List<PlaceOfInterest>

    @Query("DELETE FROM PlaceOfInterest WHERE id = :id")
    fun deleteById(id: Long)

    @Query("SELECT * FROM PlaceOfInterest WHERE id = :id")
    fun getById(id: Long): PlaceOfInterest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun update(placeOfInterest: PlaceOfInterest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(places: List<PlaceOfInterest>)

    @Query("DELETE FROM PlaceOfInterest")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM PlaceOfInterest")
    fun getCount(): Int
}
