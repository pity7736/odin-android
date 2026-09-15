package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Query(
        """
        SELECT t.*, c.name AS categoryName, a.name AS accountName
        FROM transactions t
        JOIN categories c ON t.categoryId = c.id
        JOIN accounts a ON t.accountId = a.id
        WHERE t.id = :id
        """
    )
    fun findDetailById(id: String): Flow<TransactionDetailEntity?>
}
