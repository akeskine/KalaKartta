package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FishCatchDao {

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun insert(fishCatch: FishCatch): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    fun insertAll(fishCatches: List<FishCatch>)

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

    @Query("SELECT COUNT(*) FROM FishCatch")
    fun getCount(): Int

    @Query("SELECT fisherman, COUNT(*) as count FROM FishCatch WHERE fisherman IS NOT NULL AND fisherman != '' GROUP BY fisherman")
    fun getFishermenWithCounts(): List<FishermanCount>

    @Query("SELECT DISTINCT otherSpecies FROM FishCatch WHERE otherSpecies IS NOT NULL AND otherSpecies != '' ORDER BY otherSpecies ASC")
    fun getUniqueOtherSpecies(): List<String>

    data class FishermanCount(
        val fisherman: String,
        val count: Int
    )
}