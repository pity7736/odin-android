package dev.raiseexception.odin.accounting.presentation.accountslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
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
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
            this@AccountsListViewModel.accountLister.list(criteria).collect { outcome ->
                this@AccountsListViewModel.mutableUiState.value = when (outcome) {
                    is Outcome.Success -> {
                        val visibleAccounts = outcome.value.filter { it.type != AccountType.CREDIT_CARD }
                        if (visibleAccounts.isEmpty()) {
                            AccountsListUiState.Empty
                        } else {
                            AccountsListUiState.Content(visibleAccounts)
                        }
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
