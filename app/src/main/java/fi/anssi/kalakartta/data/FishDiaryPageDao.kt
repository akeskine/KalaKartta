package fi.anssi.kalakartta.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface FishDiaryPageDao {
    @Query("SELECT * FROM FishDiaryPage ORDER BY startDate DESC")
    fun getAll(): List<FishDiaryPage>

    @Query("SELECT * FROM FishDiaryPage WHERE id = :id")
    fun getById(id: Long): FishDiaryPage?

    @RawQuery
    fun search(query: SupportSQLiteQuery): List<FishDiaryPage>

    @RawQuery
    fun count(query: SupportSQLiteQuery): Int

    @Insert
    fun insert(page: FishDiaryPage): Long

    @Update
    fun update(page: FishDiaryPage)

    @Delete
    fun delete(page: FishDiaryPage)
}
