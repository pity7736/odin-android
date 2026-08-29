package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.IncomeRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

class IncomeCreator(
    private val accountRepository: AccountRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryCreator: CategoryCreator,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {

    suspend fun create(
        accountId: String,
        amount: String,
        date: LocalDate?,
        categoryInput: CategoryInput,
        description: String
    ): Outcome<Income> {
        val account = when (val outcome = this.accountRepository.findById(accountId, AccountCriteria())) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return Outcome.Failure(
                IncomeCreationError.StorageFailure(
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
                    val result = this.categoryCreator.create(categoryInput.categoryName, CategoryType.INCOME, "", null)
                    when (result) {
                        is Outcome.Success -> result.value.id
                        is Outcome.Failure -> return@run Outcome.Failure(
                            IncomeCreationError.StorageFailure(
                                internalMessage = result.error.internalMessage,
                                externalMessage = result.error.externalMessage
                            )
                        )
                    }
                }
            }
            val income = when (
                val creationOutcome = account.createIncome(
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
            when (val saveOutcome = this.incomeRepository.add(income)) {
                is Outcome.Success -> Outcome.Success(income)
                is Outcome.Failure -> Outcome.Failure(
                    IncomeCreationError.StorageFailure(
                        internalMessage = saveOutcome.error.internalMessage,
                        externalMessage = saveOutcome.error.externalMessage
                    )
                )
            }
        }
    }

    private suspend fun resolveExistingCategory(categoryId: String): Outcome<String> {
        val categories = when (val outcome = this.categoryRepository.getAll().first()) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return Outcome.Failure(
                IncomeCreationError.CryptoFailure(
                    internalMessage = outcome.error.internalMessage,
                    externalMessage = outcome.error.externalMessage
                )
            )
        }
        val category = categories.firstOrNull { it.id == categoryId }
            ?: return Outcome.Failure(
                IncomeCreationError.CategoryNotFound(
                    internalMessage = "Category with id $categoryId not found",
                    externalMessage = "La categoría seleccionada no existe."
                )
            )
        if (category.type != CategoryType.INCOME) {
            return Outcome.Failure(
                IncomeCreationError.CategoryWrongType(
                    internalMessage = "Category ${category.id} is of type ${category.type}, expected INCOME",
                    externalMessage = "La categoría seleccionada no es de tipo ingreso."
                )
            )
        }
        return Outcome.Success(category.id)
    }
}
