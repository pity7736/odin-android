package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: CategoryEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE LOWER(name) = LOWER(:name) AND type = :type)")
    suspend fun existsByNameAndType(name: String, type: String): Boolean

    @Query("SELECT * FROM categories WHERE id = :id")
    fun findById(id: String): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type")
    fun findByType(type: String): Flow<List<CategoryEntity>>
}
