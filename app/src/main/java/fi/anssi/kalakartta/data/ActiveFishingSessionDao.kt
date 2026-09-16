package fi.anssi.kalakartta.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ActiveFishingSessionDao {
    @Query("SELECT * FROM ActiveFishingSession WHERE singletonId = 1")
    fun get(): ActiveFishingSession?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(state: ActiveFishingSession)

    @Update
    fun update(state: ActiveFishingSession)

    @Query("DELETE FROM ActiveFishingSession WHERE singletonId = 1")
    fun delete()
}
