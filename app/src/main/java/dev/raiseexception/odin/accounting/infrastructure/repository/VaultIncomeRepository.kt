package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.accounting.infrastructure.serialization.IncomeRecord
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import kotlinx.serialization.json.Json

class VaultIncomeRepository(
    private val encryptedRecordStore: EncryptedRecordStore,
    private val json: Json = Json
) : IncomeRepository {

    override suspend fun add(income: Income): Outcome<Unit> {
        val plaintext = this.json.encodeToString(IncomeRecord.serializer(), this.toRecord(income))
            .encodeToByteArray()
        return when (val saveOutcome = this.encryptedRecordStore.save(income.id, plaintext)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(
                IncomeCreationError.CryptoFailure(
                    internalMessage = saveOutcome.error.internalMessage,
                    externalMessage = "Algo salió mal. Intente de nuevo más tarde"
                )
            )
        }
    }

    private fun toRecord(income: Income) = IncomeRecord(
        recordType = IncomeRecord.INCOME_RECORD_TYPE,
        id = income.id,
        accountId = income.accountId,
        amount = income.amount.amount.toPlainString(),
        currency = income.amount.currency.name,
        date = income.date.toString(),
        categoryId = income.categoryId,
        description = income.description,
        createdAt = income.createdAt.toString()
    )
}
