package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountDetailViewModel(
    private val accountId: String,
    private val accountFinder: AccountFinder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<AccountDetailUiState>(AccountDetailUiState.Loading)
    val uiState: StateFlow<AccountDetailUiState> = this.mutableUiState.asStateFlow()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            this@AccountDetailViewModel.mutableUiState.value = when (
                val outcome = this@AccountDetailViewModel.accountFinder.find(this@AccountDetailViewModel.accountId)
            ) {
                is Outcome.Success -> AccountDetailUiState.Content(outcome.value)
                is Outcome.Failure -> when (outcome.error) {
                    is AccountLookupError.NotFound -> AccountDetailUiState.NotFound
                    else -> AccountDetailUiState.Error(outcome.error.externalMessage)
                }
            }
        }
    }
}
