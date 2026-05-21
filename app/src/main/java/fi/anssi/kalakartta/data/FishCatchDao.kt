package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FishCatchDao {

    @Insert
    fun insert(fishCatch: FishCatch): Long

    @Query("SELECT * FROM FishCatch")
    fun getAll(): List<FishCatch>

    @Query("DELETE FROM FishCatch WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM FishCatch")
    fun deleteAll()

    @Query("SELECT * FROM FishCatch WHERE id = :id")
    fun getById(id: Long): FishCatch?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun update(fishCatch: FishCatch)
}