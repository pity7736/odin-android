package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.accounting.domain.model.Transfer
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.accounting.domain.repository.TransferRepository
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class TransferCreator(
    private val accountRepository: AccountRepository,
    private val transferRepository: TransferRepository,
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val tagResolver: TagResolver,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {

    suspend fun create(
        sourceAccountId: String,
        destinationAccountId: String,
        amount: String,
        date: String,
        chargeGmf: Boolean
    ): Outcome<Transfer> {
        val accountError = this.validateAccountIds(sourceAccountId, destinationAccountId)
        if (accountError != null) return Outcome.Failure(accountError)
        val sourceAccount = when (val outcome = this.loadAccount(sourceAccountId)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return outcome
        }
        val destinationAccount = when (val outcome = this.loadAccount(destinationAccountId)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return outcome
        }
        val categoryId = when (val outcome = this.findTransferCategoryId()) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return outcome
        }
        val accounts = TransferAccounts(source = sourceAccount, destination = destinationAccount)
        return this.persistTransfer(accounts, amount, date, categoryId, chargeGmf)
    }

    private fun validateAccountIds(
        sourceAccountId: String,
        destinationAccountId: String
    ): TransferCreationError.InvalidInput? {
        val sourceError = if (sourceAccountId.isBlank()) "Selecciona una cuenta origen." else null
        val destError = if (destinationAccountId.isBlank()) "Selecciona una cuenta destino." else null
        if (sourceError != null || destError != null) {
            return TransferCreationError.InvalidInput(
                amountError = null,
                dateError = null,
                sourceAccountError = sourceError,
                destinationAccountError = destError
            )
        }
        return null
    }

    private suspend fun loadAccount(accountId: String): Outcome<Account> =
        when (
            val outcome = this.accountRepository.findById(
                accountId,
                AccountCriteria(includeIncomes = true, includeExpenses = true)
            ).first()
        ) {
            is Outcome.Success -> outcome
            is Outcome.Failure -> Outcome.Failure(storageFailure(outcome.error))
        }

    private suspend fun persistTransfer(
        accounts: TransferAccounts,
        amount: String,
        date: String,
        categoryId: String,
        chargeGmf: Boolean
    ): Outcome<Transfer> = this.transactionRunner.run {
        val gmfTagIds = when (val outcome = this.resolveGmfTags(accounts.source, chargeGmf, amount)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return@run outcome
        }
        val transfer = when (
            val outcome = Transfer.create(
                sourceAccount = accounts.source,
                destinationAccount = accounts.destination,
                amount = amount,
                date = date,
                categoryId = categoryId,
                chargeGmf = chargeGmf,
                gmfTagIds = gmfTagIds,
                clock = this.clock
            )
        ) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return@run outcome
        }
        this.saveExpenseAndIncome(transfer).let { if (it is Outcome.Failure) return@run it }
        when (val outcome = this.transferRepository.add(transfer)) {
            is Outcome.Failure -> Outcome.Failure(storageFailure(outcome.error))
            is Outcome.Success -> Outcome.Success(transfer)
        }
    }

    private suspend fun resolveGmfTags(
        sourceAccount: Account,
        chargeGmf: Boolean,
        amount: String
    ): Outcome<List<String>> {
        val treatment = sourceAccount.funding.gmfTreatment
        val taxIsZero = sourceAccount.gmfTaxOn(amount).signum() == 0
        if (!chargeGmf || taxIsZero || treatment !is GmfTreatment.Taxed) return Outcome.Success(emptyList())
        return when (val outcome = this.tagResolver.resolve(treatment.tagNames.map { TagInput.New(it) })) {
            is Outcome.Success -> outcome
            is Outcome.Failure -> Outcome.Failure(storageFailure(outcome.error))
        }
    }

    private suspend fun saveExpenseAndIncome(transfer: Transfer): Outcome<Unit> {
        when (val outcome = this.expenseRepository.add(transfer.expense)) {
            is Outcome.Failure -> return Outcome.Failure(storageFailure(outcome.error))
            is Outcome.Success -> Unit
        }
        return when (val outcome = this.incomeRepository.add(transfer.income)) {
            is Outcome.Failure -> Outcome.Failure(storageFailure(outcome.error))
            is Outcome.Success -> outcome
        }
    }

    private suspend fun findTransferCategoryId(): Outcome<String> {
        val categories = when (
            val outcome = this.categoryRepository.findByType(CategoryType.TRANSFER).first()
        ) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return Outcome.Failure(storageFailure(outcome.error))
        }
        val transferCategory = categories.firstOrNull()
            ?: return Outcome.Failure(
                TransferCreationError.TransferCategoryNotFound(
                    internalMessage = "Transfer category not found",
                    externalMessage = "No se encontró la categoría de transferencia."
                )
            )
        return Outcome.Success(transferCategory.id)
    }

    private fun storageFailure(error: DomainError) = TransferCreationError.StorageFailure(
        internalMessage = error.internalMessage,
        externalMessage = error.externalMessage
    )

    private data class TransferAccounts(val source: Account, val destination: Account)
}
