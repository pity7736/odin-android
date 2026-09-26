package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.ExpenseUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class ExpenseUpdater(
    private val transactionFinder: TransactionFinder,
    private val accountFinder: AccountFinder,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryCreator: CategoryCreator,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {

    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    suspend fun update(
        expenseId: String,
        amount: String,
        date: String,
        categoryInput: CategoryInput,
        description: String
    ): Outcome<Expense> {
        val original = when (val lookupOutcome = this.findEditableExpense(expenseId)) {
            is Outcome.Success -> lookupOutcome.value
            is Outcome.Failure -> return lookupOutcome
        }
        val account = when (val accountOutcome = this.accountFinder.find(original.accountId, this.criteria).first()) {
            is Outcome.Success -> accountOutcome.value
            is Outcome.Failure -> return this.storageFailure(accountOutcome.error)
        }
        return this.transactionRunner.run {
            val resolution = when (val resolutionOutcome = this.resolveCategory(categoryInput)) {
                is Outcome.Success -> resolutionOutcome.value
                is Outcome.Failure -> return@run resolutionOutcome
            }
            val categoryId = if (resolution is CategoryResolution.Resolved) resolution.categoryId else ""
            val editOutcome = account.editExpense(expenseId, amount, date, categoryId, description, this.clock)
            when (editOutcome) {
                is Outcome.Success -> this.persist(editOutcome.value)
                is Outcome.Failure -> this.withResolutionError(editOutcome, resolution)
            }
        }
    }

    private suspend fun findEditableExpense(expenseId: String): Outcome<Expense> {
        val detail = when (val findOutcome = this.transactionFinder.find(expenseId).first()) {
            is Outcome.Success -> findOutcome.value
            is Outcome.Failure -> return if (findOutcome.error is TransactionLookupError.NotFound) {
                findOutcome
            } else {
                this.storageFailure(findOutcome.error)
            }
        }
        val expense = detail.transaction as? Expense
            ?: return Outcome.Failure(
                TransactionLookupError.NotFound(
                    internalMessage = "Transaction $expenseId is not an expense",
                    externalMessage = "Transacción no encontrada"
                )
            )
        if (detail.isTransfer) {
            return Outcome.Failure(
                ExpenseUpdateError.TransferNotEditable(internalMessage = "Expense $expenseId belongs to a transfer")
            )
        }
        return Outcome.Success(expense)
    }

    private suspend fun resolveCategory(categoryInput: CategoryInput): Outcome<CategoryResolution> =
        when (categoryInput) {
            is CategoryInput.Existing -> this.resolveExistingCategory(categoryInput.categoryId)
            is CategoryInput.New -> this.resolveNewCategory(categoryInput.categoryName)
        }

    private suspend fun resolveExistingCategory(categoryId: String): Outcome<CategoryResolution> =
        when (val lookupOutcome = this.categoryRepository.findById(categoryId).first()) {
            is Outcome.Failure -> if (lookupOutcome.error is CategoryLookupError.NotFound) {
                Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no existe."))
            } else {
                this.storageFailure(lookupOutcome.error)
            }
            is Outcome.Success -> if (lookupOutcome.value.type == CategoryType.EXPENSE) {
                Outcome.Success(CategoryResolution.Resolved(lookupOutcome.value.id))
            } else {
                Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no es de tipo gasto."))
            }
        }

    private suspend fun resolveNewCategory(categoryName: String): Outcome<CategoryResolution> =
        when (val creationOutcome = this.categoryCreator.create(categoryName, CategoryType.EXPENSE, "", null)) {
            is Outcome.Success -> Outcome.Success(CategoryResolution.Resolved(creationOutcome.value.id))
            is Outcome.Failure -> when (val error = creationOutcome.error) {
                is CategoryCreationError.InvalidInput -> Outcome.Success(CategoryResolution.Rejected(error.nameError))
                is CategoryCreationError.DuplicateName ->
                    Outcome.Success(CategoryResolution.Rejected(error.externalMessage))
                else -> this.storageFailure(error)
            }
        }

    private fun withResolutionError(editFailure: Outcome.Failure, resolution: CategoryResolution): Outcome.Failure {
        val domainError = editFailure.error as? ExpenseUpdateError.InvalidInput ?: return editFailure
        val resolutionError = if (resolution is CategoryResolution.Rejected) resolution.categoryError else null
        return Outcome.Failure(
            ExpenseUpdateError.InvalidInput(
                amountError = domainError.amountError,
                dateError = domainError.dateError,
                categoryError = resolutionError ?: domainError.categoryError,
                descriptionError = domainError.descriptionError
            )
        )
    }

    private suspend fun persist(edited: Expense): Outcome<Expense> =
        when (val updateOutcome = this.expenseRepository.update(edited)) {
            is Outcome.Success -> Outcome.Success(edited)
            is Outcome.Failure -> this.storageFailure(updateOutcome.error)
        }

    private fun storageFailure(error: DomainError): Outcome.Failure =
        Outcome.Failure(ExpenseUpdateError.StorageFailure(internalMessage = error.internalMessage))

    private sealed interface CategoryResolution {
        data class Resolved(val categoryId: String) : CategoryResolution
        data class Rejected(val categoryError: String?) : CategoryResolution
    }
}
