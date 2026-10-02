package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert
    suspend fun insert(tag: TagEntity)

    @Query("SELECT * FROM tags")
    fun getAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun findById(id: String): TagEntity?

    @Query("SELECT * FROM tags WHERE normalizedName = :normalizedName")
    suspend fun findByNormalizedName(normalizedName: String): TagEntity?

    @Query(
        """
        SELECT tags.* FROM tags
        JOIN expense_tags ON expense_tags.tagId = tags.id
        WHERE expense_tags.expenseId = :expenseId
        """
    )
    fun findByExpenseId(expenseId: String): Flow<List<TagEntity>>

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM expense_tags)")
    suspend fun deleteUnused()
}
