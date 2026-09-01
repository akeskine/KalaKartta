package fi.anssi.kalakartta.data

import androidx.room.*

@Dao
interface FishDiaryPageDao {
    @Query("SELECT * FROM FishDiaryPage ORDER BY startDate DESC")
    fun getAll(): List<FishDiaryPage>

    @Query("SELECT * FROM FishDiaryPage WHERE id = :id")
    fun getById(id: Long): FishDiaryPage?

    @Insert
    fun insert(page: FishDiaryPage): Long

    @Update
    fun update(page: FishDiaryPage)

    @Delete
    fun delete(page: FishDiaryPage)
}
