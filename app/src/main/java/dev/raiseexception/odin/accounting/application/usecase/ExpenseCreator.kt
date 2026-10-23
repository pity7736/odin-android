package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.TagResolutionError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.TransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

@Suppress("LongParameterList")
class ExpenseCreator(
    private val accountRepository: AccountRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryRepository: CategoryRepository,
    private val categoryCreator: CategoryCreator,
    private val tagResolver: TagResolver,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock = Clock.System
) {

    suspend fun create(
        accountId: String,
        amount: String,
        date: String,
        categoryInput: CategoryInput,
        description: String,
        tagInputs: List<TagInput>
    ): Outcome<Expense> {
        val account = when (
            val outcome = this.accountRepository.findById(
                accountId,
                AccountCriteria(includeIncomes = true, includeExpenses = true)
            ).first()
        ) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return Outcome.Failure(
                ExpenseCreationError.StorageFailure(
                    internalMessage = outcome.error.internalMessage,
                    externalMessage = outcome.error.externalMessage
                )
            )
        }
        return this.transactionRunner.run {
            val categoryId = when (val categoryOutcome = this.resolveCategory(categoryInput)) {
                is Outcome.Success -> categoryOutcome.value
                is Outcome.Failure -> return@run categoryOutcome
            }
            val tagIds = when (val tagOutcome = this.resolveTags(tagInputs)) {
                is Outcome.Success -> tagOutcome.value
                is Outcome.Failure -> return@run tagOutcome
            }
            val gmfTagIds = when (val gmfTagOutcome = this.resolveGmfTags(account, amount)) {
                is Outcome.Success -> gmfTagOutcome.value
                is Outcome.Failure -> return@run gmfTagOutcome
            }
            val expense = when (
                val creationOutcome = account.createExpense(
                    amount = amount,
                    date = date,
                    categoryId = categoryId,
                    description = description,
                    tagIds = tagIds,
                    chargeGmf = true,
                    gmfTagIds = gmfTagIds,
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

    private suspend fun resolveCategory(categoryInput: CategoryInput): Outcome<String> = when (categoryInput) {
        is CategoryInput.Existing -> this.resolveExistingCategory(categoryInput.categoryId)
        is CategoryInput.New -> this.resolveNewCategory(categoryInput.categoryName)
    }

    private suspend fun resolveGmfTags(account: Account, amount: String): Outcome<List<String>> {
        if (account.gmfTaxOn(amount).signum() == 0) return Outcome.Success(emptyList())
        return when (val treatment = account.funding.gmfTreatment) {
            is GmfTreatment.Exempt -> Outcome.Success(emptyList())
            is GmfTreatment.Taxed -> this.resolveTags(treatment.tagNames.map { TagInput.New(it) })
        }
    }

    private suspend fun resolveTags(tagInputs: List<TagInput>): Outcome<List<String>> =
        when (val resolution = this.tagResolver.resolve(tagInputs)) {
            is Outcome.Success -> resolution
            is Outcome.Failure -> Outcome.Failure(
                when (val error = resolution.error) {
                    is TagResolutionError.InvalidName -> ExpenseCreationError.InvalidInput(
                        amountError = null,
                        dateError = null,
                        categoryError = null,
                        tagsError = error.externalMessage
                    )
                    else -> ExpenseCreationError.StorageFailure(
                        internalMessage = error.internalMessage,
                        externalMessage = error.externalMessage
                    )
                }
            )
        }

    private suspend fun resolveNewCategory(categoryName: String): Outcome<String> {
        val result = this.categoryCreator.create(categoryName, CategoryType.EXPENSE, "", null)
        return when (result) {
            is Outcome.Success -> Outcome.Success(result.value.id)
            is Outcome.Failure -> Outcome.Failure(
                when (result.error) {
                    is CategoryCreationError.InvalidInput -> ExpenseCreationError.InvalidInput(
                        amountError = null,
                        dateError = null,
                        categoryError = result.error.nameError
                    )
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
                ExpenseCreationError.StorageFailure(
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
