package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FishSpeciesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(species: FishSpecies)

    @Delete
    fun delete(species: FishSpecies)

    @Query("SELECT * FROM FishSpecies ORDER BY sortOrder ASC")
    fun getAll(): List<FishSpecies>

    @Query("SELECT * FROM FishSpecies WHERE id = :id")
    fun getById(id: String): FishSpecies?

    @Query("DELETE FROM FishSpecies")
    fun deleteAll()
}
