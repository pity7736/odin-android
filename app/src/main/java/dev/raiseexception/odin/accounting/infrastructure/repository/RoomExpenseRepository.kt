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
            this.transactionDao.insert(expense.toEntity(gmfMovementId = null))
            this.expenseTagDao.insertAll(expense.toExpenseTagEntities())
            this.insertGmfTax(expense)
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to add expense"))
        }

    override suspend fun update(expense: Expense): Outcome<Unit> =
        try {
            this.transactionDao.update(expense.toEntity(gmfMovementId = null))
            this.expenseTagDao.deleteByExpenseId(expense.id)
            this.expenseTagDao.insertAll(expense.toExpenseTagEntities())
            this.deleteStoredGmfTaxOf(expense.id)
            this.insertGmfTax(expense)
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to update expense"))
        }

    override suspend fun delete(expenseId: String): Outcome<Unit> =
        try {
            this.deleteStoredGmfTaxOf(expenseId)
            this.expenseTagDao.deleteByExpenseId(expenseId)
            this.transactionDao.deleteById(expenseId)
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to delete expense"))
        }

    override fun findSpendingBetween(start: LocalDate, end: LocalDate): Flow<Outcome<List<Expense>>> =
        this.transactionDao.findSpendingBetween(start.toString(), end.toString())
            .map<_, Outcome<List<Expense>>> { rows ->
                Outcome.Success(rows.map { it.transaction.toExpense(it.tagIds, null) })
            }
            .catch { e ->
                if (e is SQLiteException) {
                    emit(Outcome.Failure(StorageError(e.message ?: "Failed to list spending")))
                } else {
                    throw e
                }
            }

    private suspend fun insertGmfTax(movement: Expense) {
        val gmfTax = movement.gmfTax ?: return
        this.transactionDao.insert(gmfTax.toEntity(gmfMovementId = movement.id))
        this.expenseTagDao.insertAll(gmfTax.toExpenseTagEntities())
    }

    private suspend fun deleteStoredGmfTaxOf(movementId: String) {
        this.expenseTagDao.deleteByGmfMovementId(movementId)
        this.transactionDao.deleteByGmfMovementId(movementId)
    }
}
