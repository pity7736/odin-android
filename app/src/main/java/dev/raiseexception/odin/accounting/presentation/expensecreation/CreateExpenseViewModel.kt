package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CreateExpenseViewModel(
    private val accountId: String,
    private val expenseCreator: ExpenseCreator,
    private val categoryLister: CategoryLister,
    private val accountFinder: AccountFinder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateExpenseUiState>(CreateExpenseUiState.Loading)
    val uiState: StateFlow<CreateExpenseUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            val categoriesOutcome = this@CreateExpenseViewModel.categoryLister
                .list(CategoryType.EXPENSE, "").first()
            val accountOutcome = this@CreateExpenseViewModel.accountFinder
                .find(this@CreateExpenseViewModel.accountId).first()
            this@CreateExpenseViewModel.mutableUiState.value = when {
                categoriesOutcome is Outcome.Failure ->
                    CreateExpenseUiState.Error(categoriesOutcome.error.externalMessage)
                accountOutcome is Outcome.Failure ->
                    CreateExpenseUiState.Error(accountOutcome.error.externalMessage)
                else -> {
                    val categories = (categoriesOutcome as Outcome.Success).value
                    val account = (accountOutcome as Outcome.Success).value
                    val createdAt = account.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    CreateExpenseUiState.Idle(categories, createdAt)
                }
            }
        }
    }

    fun save(amount: String, date: String, categoryInput: CategoryInput, description: String) {
        if (this.mutableUiState.value is CreateExpenseUiState.Saving) return
        val snapshot = currentSnapshot()
        this.mutableUiState.value = CreateExpenseUiState.Saving
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateExpenseViewModel.expenseCreator.create(
                accountId = this@CreateExpenseViewModel.accountId,
                amount = amount,
                date = date,
                categoryInput = categoryInput,
                description = description
            )
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(
                    NavigationTarget.AccountDetail(this@CreateExpenseViewModel.accountId)
                )
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error, snapshot)
            }
        }
    }

    private fun mapError(error: DomainError, snapshot: FormSnapshot): CreateExpenseUiState {
        return when (error) {
            is ExpenseCreationError.InvalidInput -> CreateExpenseUiState.ValidationError(
                categories = snapshot.categories,
                accountCreatedAt = snapshot.accountCreatedAt,
                amountError = error.amountError,
                dateError = error.dateError,
                categoryError = error.categoryError,
                descriptionError = error.descriptionError
            )
            else -> CreateExpenseUiState.Error(error.externalMessage)
        }
    }

    private fun currentSnapshot() = when (val current = this.mutableUiState.value) {
        is CreateExpenseUiState.Idle -> FormSnapshot(current.categories, current.accountCreatedAt)
        is CreateExpenseUiState.ValidationError -> FormSnapshot(current.categories, current.accountCreatedAt)
        else -> FormSnapshot(emptyList(), EPOCH_DATE)
    }

    private data class FormSnapshot(val categories: List<Category>, val accountCreatedAt: LocalDate)

    companion object {
        private val EPOCH_DATE = LocalDate(1970, 1, 1)
    }
}
