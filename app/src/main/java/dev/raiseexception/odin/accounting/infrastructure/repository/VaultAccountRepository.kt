package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.infrastructure.serialization.AccountRecord
import dev.raiseexception.odin.accounting.infrastructure.serialization.IncomeRecord
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.math.BigDecimal

class VaultAccountRepository(
    private val encryptedRecordStore: EncryptedRecordStore,
    private val json: Json = Json
) : AccountRepository {

    override suspend fun existsByName(name: String): Outcome<Boolean> {
        val accountRecords = when (val decryptOutcome = this.decryptedAccountRecords()) {
            is Outcome.Success -> decryptOutcome.value
            is Outcome.Failure -> return decryptOutcome
        }
        return Outcome.Success(accountRecords.any { it.name.equals(name, ignoreCase = true) })
    }

    override suspend fun add(account: Account): Outcome<Unit> {
        val plaintext = this.json.encodeToString(AccountRecord.serializer(), this.toRecord(account))
            .encodeToByteArray()
        return when (val saveOutcome = this.encryptedRecordStore.save(account.id, plaintext)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> this.cryptoFailure(saveOutcome.error.internalMessage)
        }
    }

    override suspend fun findById(id: String, criteria: AccountCriteria): Outcome<Account> {
        val accountRecords = when (val decryptOutcome = this.decryptedAccountRecords()) {
            is Outcome.Success -> decryptOutcome.value
            is Outcome.Failure -> return Outcome.Failure(
                AccountLookupError.StorageFailure(
                    internalMessage = decryptOutcome.error.internalMessage,
                    externalMessage = "Error al cargar la cuenta"
                )
            )
        }
        val matchingRecord = accountRecords.firstOrNull { it.id == id }
            ?: return Outcome.Failure(
                AccountLookupError.NotFound(
                    internalMessage = "Account with id $id not found",
                    externalMessage = "Cuenta no encontrada"
                )
            )
        if (!criteria.includeIncomes) {
            return Outcome.Success(this.toAccount(matchingRecord, emptyList()))
        }
        val incomeRecords = when (val decryptOutcome = this.decryptedIncomeRecords()) {
            is Outcome.Success -> decryptOutcome.value
            is Outcome.Failure -> return Outcome.Failure(
                AccountLookupError.StorageFailure(
                    internalMessage = decryptOutcome.error.internalMessage,
                    externalMessage = "Error al cargar la cuenta"
                )
            )
        }
        val accountIncomes = incomeRecords
            .filter { it.accountId == id }
            .map { this.toIncome(it) }
        return Outcome.Success(this.toAccount(matchingRecord, accountIncomes))
    }

    override fun getAll(criteria: AccountCriteria): Flow<Outcome<List<Account>>> = flow {
        when (val outcome = this@VaultAccountRepository.decryptedAccountRecords()) {
            is Outcome.Failure -> emit(outcome)
            is Outcome.Success -> emit(
                Outcome.Success(
                    outcome.value.sortedBy { it.id }.map { this@VaultAccountRepository.toAccount(it, emptyList()) }
                )
            )
        }
    }

    private suspend fun decryptedAccountRecords(): Outcome<List<AccountRecord>> {
        val records = when (val readOutcome = this.encryptedRecordStore.readAll()) {
            is Outcome.Success -> readOutcome.value
            is Outcome.Failure -> return this.cryptoFailure(readOutcome.error.internalMessage)
        }
        val accountRecords = records
            .mapNotNull { record ->
                try {
                    this.json.decodeFromString(AccountRecord.serializer(), record.data.decodeToString())
                } catch (@Suppress("SwallowedException") exception: SerializationException) {
                    null
                }
            }
            .filter { it.recordType == AccountRecord.ACCOUNT_RECORD_TYPE }
        return Outcome.Success(accountRecords)
    }

    private suspend fun decryptedIncomeRecords(): Outcome<List<IncomeRecord>> {
        val records = when (val readOutcome = this.encryptedRecordStore.readAll()) {
            is Outcome.Success -> readOutcome.value
            is Outcome.Failure -> return this.cryptoFailure(readOutcome.error.internalMessage)
        }
        val incomeRecords = records
            .mapNotNull { record ->
                try {
                    this.json.decodeFromString(IncomeRecord.serializer(), record.data.decodeToString())
                } catch (@Suppress("SwallowedException") exception: SerializationException) {
                    null
                }
            }
            .filter { it.recordType == IncomeRecord.INCOME_RECORD_TYPE }
        return Outcome.Success(incomeRecords)
    }

    private fun toAccount(record: AccountRecord, incomes: List<Income>): Account = Account.restore(
        id = record.id,
        name = record.name,
        initialBalance = Money.of(BigDecimal(record.amount), Currency.valueOf(record.currency)),
        type = AccountType.valueOf(record.accountType),
        description = record.description,
        createdAt = Instant.parse(record.createdAt),
        incomes = incomes
    )

    private fun toIncome(record: IncomeRecord): Income = Income.restore(
        id = record.id,
        accountId = record.accountId,
        amount = Money.of(BigDecimal(record.amount), Currency.valueOf(record.currency)),
        date = LocalDate.parse(record.date),
        categoryId = record.categoryId,
        description = record.description,
        createdAt = Instant.parse(record.createdAt)
    )

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
}
