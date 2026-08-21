package fi.anssi.kalakartta.data

import androidx.room.*

@Dao
interface FishingSessionDao {
    @Insert
    fun insert(session: FishingSession): Long

    @Update
    fun update(session: FishingSession)

    @Query("SELECT * FROM FishingSession WHERE id = :id")
    fun getById(id: Long): FishingSession?

    @Query("SELECT * FROM FishingSession WHERE endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun getFinishedSessions(): List<FishingSession>

    @Query("SELECT * FROM FishingSession ORDER BY startedAt DESC")
    fun getAll(): List<FishingSession>

    @Query("DELETE FROM FishingSession WHERE id = :id")
    fun deleteById(id: Long)

    @Query("SELECT * FROM FishingSession WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun getActiveSession(): FishingSession?

    @Query("DELETE FROM FishingSession")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM FishingSession")
    fun getCount(): Int
}
