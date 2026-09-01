package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class ExpenseCreator(
    private val accountRepository: AccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryCreator: CategoryCreator,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {

    suspend fun create(
        accountId: String,
        amount: String,
        date: String,
        categoryInput: CategoryInput,
        description: String
    ): Outcome<Expense> {
        val account = when (val outcome = this.accountRepository.findById(accountId, AccountCriteria())) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return Outcome.Failure(
                ExpenseCreationError.StorageFailure(
                    internalMessage = outcome.error.internalMessage,
                    externalMessage = outcome.error.externalMessage
                )
            )
        }
        return this.transactionRunner.run {
            val categoryId = when (categoryInput) {
                is CategoryInput.Existing -> {
                    val result = this.resolveExistingCategory(categoryInput.categoryId)
                    if (result is Outcome.Failure) return@run result
                    (result as Outcome.Success).value
                }
                is CategoryInput.New -> {
                    val result = this.resolveNewCategory(categoryInput.categoryName)
                    if (result is Outcome.Failure) return@run result
                    (result as Outcome.Success).value
                }
            }
            val expense = when (
                val creationOutcome = account.createExpense(
                    amount = amount,
                    date = date,
                    categoryId = categoryId,
                    description = description,
                    clock = this.clock
                )
            ) {
                is Outcome.Success -> creationOutcome.value
                is Outcome.Failure -> return@run creationOutcome
            }
            when (val saveOutcome = this.expenseRepository.add(expense)) {
                is Outcome.Success -> Outcome.Success(expense)
                is Outcome.Failure -> Outcome.Failure(
                    ExpenseCreationError.StorageFailure(
                        internalMessage = saveOutcome.error.internalMessage,
                        externalMessage = saveOutcome.error.externalMessage
                    )
                )
            }
        }
    }

    private suspend fun resolveNewCategory(categoryName: String): Outcome<String> {
        val result = this.categoryCreator.create(categoryName, CategoryType.EXPENSE, "", null)
        return when (result) {
            is Outcome.Success -> Outcome.Success(result.value.id)
            is Outcome.Failure -> Outcome.Failure(
                when (result.error) {
                    is CategoryCreationError.DuplicateName -> ExpenseCreationError.InvalidInput(
                        amountError = null,
                        dateError = null,
                        categoryError = result.error.externalMessage
                    )
                    else -> ExpenseCreationError.StorageFailure(
                        internalMessage = result.error.internalMessage,
                        externalMessage = result.error.externalMessage
                    )
                }
            )
        }
    }

    private suspend fun resolveExistingCategory(categoryId: String): Outcome<String> {
        val categories = when (val outcome = this.categoryRepository.getAll().first()) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return Outcome.Failure(
                ExpenseCreationError.CryptoFailure(
                    internalMessage = outcome.error.internalMessage,
                    externalMessage = outcome.error.externalMessage
                )
            )
        }
        val category = categories.firstOrNull { it.id == categoryId }
            ?: return Outcome.Failure(
                ExpenseCreationError.CategoryNotFound(
                    internalMessage = "Category with id $categoryId not found",
                    externalMessage = "La categoría seleccionada no existe."
                )
            )
        if (category.type != CategoryType.EXPENSE) {
            return Outcome.Failure(
                ExpenseCreationError.CategoryWrongType(
                    internalMessage = "Category ${category.id} is of type ${category.type}, expected EXPENSE",
                    externalMessage = "La categoría seleccionada no es de tipo gasto."
                )
            )
        }
        return Outcome.Success(category.id)
    }
}
