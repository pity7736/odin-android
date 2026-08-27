package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.infrastructure.serialization.AccountRecord
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import dev.raiseexception.odin.shared.infrastructure.vault.StoredRecord
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class VaultAccountRepository(
    private val encryptedRecordStore: EncryptedRecordStore,
    private val json: Json = Json
) : AccountRepository {

    override suspend fun existsByName(name: String): Outcome<Boolean> {
        val records = when (val readOutcome = this.encryptedRecordStore.readAll()) {
            is Outcome.Success -> readOutcome.value
            is Outcome.Failure -> return this.cryptoFailure(readOutcome.error.internalMessage)
        }
        return this.matchName(records, name)
    }

    override suspend fun add(account: Account): Outcome<Unit> {
        val plaintext = this.json.encodeToString(AccountRecord.serializer(), this.toRecord(account))
            .encodeToByteArray()
        return when (val saveOutcome = this.encryptedRecordStore.save(account.id, plaintext)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> this.cryptoFailure(saveOutcome.error.internalMessage)
        }
    }

    private fun matchName(records: List<StoredRecord>, name: String): Outcome<Boolean> = try {
        val exists = records
            .map { this.json.decodeFromString(AccountRecord.serializer(), it.data.decodeToString()) }
            .filter { it.recordType == AccountRecord.ACCOUNT_RECORD_TYPE }
            .any { it.name.equals(name, ignoreCase = true) }
        Outcome.Success(exists)
    } catch (exception: SerializationException) {
        this.storageFailure(exception.message ?: "Failed to deserialize stored account record")
    }

    private fun toRecord(account: Account) = AccountRecord(
        id = account.id,
        name = account.name,
        amount = account.initialBalance.amount.toPlainString(),
        currency = account.currency.name,
        accountType = account.type.name,
        description = account.description,
        createdAt = account.createdAt.toString()
    )

    private fun cryptoFailure(internalMessage: String) = Outcome.Failure(
        AccountCreationError.CryptoFailure(
            internalMessage = internalMessage,
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )

    private fun storageFailure(internalMessage: String) = Outcome.Failure(
        AccountCreationError.StorageFailure(
            internalMessage = internalMessage,
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )
}
