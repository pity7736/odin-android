package dev.raiseexception.odin.accounting.presentation.incomecreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
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

class CreateIncomeViewModel(
    private val accountId: String,
    private val incomeCreator: IncomeCreator,
    private val categoryLister: CategoryLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateIncomeUiState>(CreateIncomeUiState.Loading)
    val uiState: StateFlow<CreateIncomeUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateIncomeViewModel.categoryLister.list(CategoryType.INCOME, "").first()
            this@CreateIncomeViewModel.mutableUiState.value = when (outcome) {
                is Outcome.Success -> CreateIncomeUiState.Idle(outcome.value)
                is Outcome.Failure -> CreateIncomeUiState.Error(outcome.error.externalMessage)
            }
        }
    }

    fun save(amount: String, date: String, categoryInput: CategoryInput, description: String) {
        if (this.mutableUiState.value is CreateIncomeUiState.Saving) return
        val categories = currentCategories()
        this.mutableUiState.value = CreateIncomeUiState.Saving
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateIncomeViewModel.incomeCreator.create(
                accountId = this@CreateIncomeViewModel.accountId,
                amount = amount,
                date = date,
                categoryInput = categoryInput,
                description = description
            )
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(
                    NavigationTarget.AccountDetail(this@CreateIncomeViewModel.accountId)
                )
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error, categories)
            }
        }
    }

    private fun mapError(error: DomainError, categories: List<Category>): CreateIncomeUiState {
        return when (error) {
            is IncomeCreationError.InvalidInput -> CreateIncomeUiState.ValidationError(
                categories = categories,
                amountError = error.amountError,
                dateError = error.dateError,
                categoryError = error.categoryError,
                descriptionError = error.descriptionError
            )
            else -> CreateIncomeUiState.Error(error.externalMessage)
        }
    }

    private fun currentCategories() = when (val current = this.mutableUiState.value) {
        is CreateIncomeUiState.Idle -> current.categories
        is CreateIncomeUiState.ValidationError -> current.categories
        is CreateIncomeUiState.Saving -> emptyList()
        else -> emptyList()
    }
}
