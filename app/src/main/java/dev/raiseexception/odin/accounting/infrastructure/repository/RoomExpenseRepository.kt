package dev.raiseexception.odin.accounting.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RoomExpenseRepository(
    private val transactionDao: TransactionDao,
    private val expenseTagDao: ExpenseTagDao
) : ExpenseRepository {

    override suspend fun add(expense: Expense): Outcome<Unit> =
        try {
            this.transactionDao.insert(expense.toEntity())
            this.expenseTagDao.insertAll(expense.toExpenseTagEntities())
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to add expense"))
        }

    override suspend fun update(expense: Expense): Outcome<Unit> =
        try {
            this.transactionDao.update(expense.toEntity())
            this.expenseTagDao.deleteByExpenseId(expense.id)
            this.expenseTagDao.insertAll(expense.toExpenseTagEntities())
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to update expense"))
        }

    override fun findSpendingBetween(start: LocalDate, end: LocalDate): Flow<Outcome<List<Expense>>> =
        this.transactionDao.findSpendingBetween(start.toString(), end.toString())
            .map<_, Outcome<List<Expense>>> { rows ->
                Outcome.Success(rows.map { it.transaction.toExpense(it.tagIds) })
            }
            .catch { e ->
                if (e is SQLiteException) {
                    emit(Outcome.Failure(StorageError(e.message ?: "Failed to list spending")))
                } else {
                    throw e
                }
            }
}
