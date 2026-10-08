package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query(
        """
        SELECT t.*, c.name AS categoryName, a.name AS accountName, tr.id IS NOT NULL AS isTransfer,
            a.type AS accountType
        FROM transactions t
        JOIN categories c ON t.categoryId = c.id
        JOIN accounts a ON t.accountId = a.id
        LEFT JOIN transfers tr ON tr.expenseId = t.id
        WHERE t.id = :id
        """
    )
    fun findDetailById(id: String): Flow<TransactionDetailEntity?>

    @Transaction
    @Query(
        """
        SELECT t.*
        FROM transactions t
        LEFT JOIN transfers tr ON tr.expenseId = t.id
        WHERE t.type = 'EXPENSE' AND t.date BETWEEN :start AND :end AND tr.id IS NULL
        """
    )
    fun findSpendingBetween(start: String, end: String): Flow<List<TransactionWithTagIds>>
}
