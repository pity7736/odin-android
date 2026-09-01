package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.accounting.infrastructure.serialization.ExpenseRecord
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import kotlinx.serialization.json.Json

class VaultExpenseRepository(
    private val encryptedRecordStore: EncryptedRecordStore,
    private val json: Json = Json
) : ExpenseRepository {

    override suspend fun add(expense: Expense): Outcome<Unit> {
        val plaintext = this.json.encodeToString(ExpenseRecord.serializer(), this.toRecord(expense))
            .encodeToByteArray()
        return when (val saveOutcome = this.encryptedRecordStore.save(expense.id, plaintext)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(
                ExpenseCreationError.CryptoFailure(
                    internalMessage = saveOutcome.error.internalMessage,
                    externalMessage = "Algo salió mal. Intente de nuevo más tarde"
                )
            )
        }
    }

    private fun toRecord(expense: Expense) = ExpenseRecord(
        id = expense.id,
        accountId = expense.accountId,
        amount = expense.amount.amount.toPlainString(),
        currency = expense.amount.currency.name,
        date = expense.date.toString(),
        categoryId = expense.categoryId,
        description = expense.description,
        createdAt = expense.createdAt.toString()
    )
}
