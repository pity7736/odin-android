package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.IncomeUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class IncomeUpdater(
    private val transactionFinder: TransactionFinder,
    private val accountFinder: AccountFinder,
    private val incomeRepository: IncomeRepository,
    private val categoryResolver: CategoryResolver,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {

    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    suspend fun update(
        incomeId: String,
        amount: String,
        date: String,
        categoryInput: CategoryInput,
        description: String
    ): Outcome<Income> {
        val original = when (val lookupOutcome = this.findEditableIncome(incomeId)) {
            is Outcome.Success -> lookupOutcome.value
            is Outcome.Failure -> return lookupOutcome
        }
        val account = when (val accountOutcome = this.accountFinder.find(original.accountId, this.criteria).first()) {
            is Outcome.Success -> accountOutcome.value
            is Outcome.Failure -> return this.storageFailure(accountOutcome.error)
        }
        return this.transactionRunner.run {
            val categoryResolution = when (
                val categoryOutcome = this.categoryResolver.resolve(categoryInput, CategoryType.INCOME)
            ) {
                is Outcome.Success -> categoryOutcome.value
                is Outcome.Failure -> return@run this.storageFailure(categoryOutcome.error)
            }
            val resolvedCategoryId = (categoryResolution as? CategoryResolution.Resolved)?.categoryId ?: ""
            val editOutcome = account.editIncome(incomeId, amount, date, resolvedCategoryId, description, this.clock)
            when (editOutcome) {
                is Outcome.Success -> this.persist(editOutcome.value)
                is Outcome.Failure -> this.withCategoryError(editOutcome, categoryResolution)
            }
        }
    }

    private suspend fun findEditableIncome(incomeId: String): Outcome<Income> {
        val detail = when (val findOutcome = this.transactionFinder.find(incomeId).first()) {
            is Outcome.Success -> findOutcome.value
            is Outcome.Failure -> return if (findOutcome.error is TransactionLookupError.NotFound) {
                findOutcome
            } else {
                this.storageFailure(findOutcome.error)
            }
        }
        val income = detail.transaction as? Income
            ?: return Outcome.Failure(
                TransactionLookupError.NotFound(
                    internalMessage = "Transaction $incomeId is not an income",
                    externalMessage = "Transacción no encontrada"
                )
            )
        if (detail.isTransfer) {
            return Outcome.Failure(
                IncomeUpdateError.TransferNotEditable(internalMessage = "Income $incomeId belongs to a transfer")
            )
        }
        return Outcome.Success(income)
    }

    private suspend fun persist(edited: Income): Outcome<Income> =
        when (val updateOutcome = this.incomeRepository.update(edited)) {
            is Outcome.Success -> Outcome.Success(edited)
            is Outcome.Failure -> this.storageFailure(updateOutcome.error)
        }

    private fun withCategoryError(
        editFailure: Outcome.Failure,
        categoryResolution: CategoryResolution
    ): Outcome.Failure {
        val domainError = editFailure.error as? IncomeUpdateError.InvalidInput ?: return editFailure
        val resolutionError = (categoryResolution as? CategoryResolution.Rejected)?.categoryError
        return Outcome.Failure(
            IncomeUpdateError.InvalidInput(
                amountError = domainError.amountError,
                dateError = domainError.dateError,
                categoryError = resolutionError ?: domainError.categoryError,
                descriptionError = domainError.descriptionError
            )
        )
    }

    private fun storageFailure(error: DomainError): Outcome.Failure =
        Outcome.Failure(IncomeUpdateError.StorageFailure(internalMessage = error.internalMessage))
}
