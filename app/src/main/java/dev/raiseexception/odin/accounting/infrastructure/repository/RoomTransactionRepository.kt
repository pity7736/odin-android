package dev.raiseexception.odin.accounting.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.accounting.domain.repository.TransactionRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class RoomTransactionRepository(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao
) : TransactionRepository {

    override fun findById(id: String): Flow<Outcome<TransactionDetail>> =
        combine(
            this.transactionDao.findDetailById(id),
            this.tagDao.findByExpenseId(id),
            this.transactionDao.findGmfTaxOf(id).distinctUntilChanged()
        ) { entity, tagEntities, gmfTaxRow ->
            this.toOutcome(id, entity, tagEntities, gmfTaxRow?.let { it.transaction.toExpense(it.tagIds, null) })
        }.catch { e ->
            if (e is SQLiteException) {
                emit(Outcome.Failure(StorageError(e.message ?: "Failed to find transaction")))
            } else {
                throw e
            }
        }

    private fun toOutcome(
        id: String,
        entity: TransactionDetailEntity?,
        tagEntities: List<TagEntity>,
        gmfTax: Expense?
    ): Outcome<TransactionDetail> =
        if (entity == null) {
            Outcome.Failure(
                TransactionLookupError.NotFound(
                    internalMessage = "Transaction with id $id not found",
                    externalMessage = "Transacción no encontrada"
                )
            )
        } else {
            val tags = tagEntities.map { it.toDomain() }.sortedWith(Tag.ALPHABETICAL_ORDER)
            Outcome.Success(entity.toDomain(tags, gmfTax))
        }
}
