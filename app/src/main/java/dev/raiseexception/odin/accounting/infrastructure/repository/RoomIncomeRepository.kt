package dev.raiseexception.odin.accounting.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError

class RoomIncomeRepository(
    private val transactionDao: TransactionDao
) : IncomeRepository {

    override suspend fun add(income: Income): Outcome<Unit> =
        try {
            this.transactionDao.insert(income.toEntity())
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to add income"))
        }
}
