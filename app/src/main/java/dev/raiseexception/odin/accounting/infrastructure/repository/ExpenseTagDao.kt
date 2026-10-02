package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseTagDao {

    @Insert
    suspend fun insertAll(expenseTags: List<ExpenseTagEntity>)

    @Query("DELETE FROM expense_tags WHERE expenseId = :expenseId")
    suspend fun deleteByExpenseId(expenseId: String)
}
