package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class CreateExpenseViewModel(
    private val accountId: String,
    private val expenseCreator: ExpenseCreator,
    private val categoryLister: CategoryLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateExpenseUiState>(CreateExpenseUiState.Loading)
    val uiState: StateFlow<CreateExpenseUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateExpenseViewModel.categoryLister.list(CategoryType.EXPENSE, "").first()
            this@CreateExpenseViewModel.mutableUiState.value = when (outcome) {
                is Outcome.Success -> CreateExpenseUiState.Idle(outcome.value)
                is Outcome.Failure -> CreateExpenseUiState.Error(outcome.error.externalMessage)
            }
        }
    }

    fun save(amount: String, date: String, categoryInput: CategoryInput, description: String) {
        if (this.mutableUiState.value is CreateExpenseUiState.Saving) return
        val categories = currentCategories()
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
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error, categories)
            }
        }
    }

    private fun mapError(error: DomainError, categories: List<Category>): CreateExpenseUiState {
        return when (error) {
            is ExpenseCreationError.InvalidInput -> CreateExpenseUiState.ValidationError(
                categories = categories,
                amountError = error.amountError,
                dateError = error.dateError,
                categoryError = error.categoryError,
                descriptionError = error.descriptionError
            )
            else -> CreateExpenseUiState.Error(error.externalMessage)
        }
    }

    private fun currentCategories() = when (val current = this.mutableUiState.value) {
        is CreateExpenseUiState.Idle -> current.categories
        is CreateExpenseUiState.ValidationError -> current.categories
        is CreateExpenseUiState.Saving -> emptyList()
        else -> emptyList()
    }
}
