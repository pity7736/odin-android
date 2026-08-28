package dev.raiseexception.odin.accounting.presentation.accountslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AccountsListViewModel(
    private val accountLister: AccountLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<AccountsListUiState>(AccountsListUiState.Loading)
    val uiState: StateFlow<AccountsListUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<AccountsListNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<AccountsListNavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            this@AccountsListViewModel.accountLister.list().collect { outcome ->
                this@AccountsListViewModel.mutableUiState.value = when (outcome) {
                    is Outcome.Success -> if (outcome.value.isEmpty()) {
                        AccountsListUiState.Empty
                    } else {
                        AccountsListUiState.Content(outcome.value)
                    }
                    is Outcome.Failure -> AccountsListUiState.Error("Error al cargar las cuentas")
                }
            }
        }
    }

    fun onAccountSelected(accountId: String) {
        this.viewModelScope.launch {
            this@AccountsListViewModel.navigationChannel.send(AccountsListNavigationTarget.AccountDetail(accountId))
        }
    }
}
