package dev.raiseexception.odin.accounting.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounting.domain.model.Transfer
import dev.raiseexception.odin.accounting.domain.repository.TransferRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError

class RoomTransferRepository(
    private val transferDao: TransferDao
) : TransferRepository {

    override suspend fun add(transfer: Transfer): Outcome<Unit> =
        try {
            this.transferDao.insert(transfer.toEntity())
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to add transfer"))
        }
}

private fun Transfer.toEntity(): TransferEntity =
    TransferEntity(
        id = id,
        expenseId = expense.id,
        incomeId = income.id,
        createdAt = createdAt.toString()
    )
