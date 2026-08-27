package dev.raiseexception.odin.accounting.presentation.accountslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AccountsListViewModel(
    private val accountRepository: AccountRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<AccountsListUiState>(AccountsListUiState.Loading)
    val uiState: StateFlow<AccountsListUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<AccountsListNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<AccountsListNavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            try {
                this@AccountsListViewModel.accountRepository.getAll().collect { accounts ->
                    this@AccountsListViewModel.mutableUiState.value = if (accounts.isEmpty()) {
                        AccountsListUiState.Empty
                    } else {
                        AccountsListUiState.Content(accounts)
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") exception: Exception) {
                this@AccountsListViewModel.mutableUiState.value =
                    AccountsListUiState.Error("Error al cargar las cuentas")
            }
        }
    }

    fun onAccountSelected(accountId: String) {
        this.viewModelScope.launch {
            this@AccountsListViewModel.navigationChannel.send(AccountsListNavigationTarget.AccountDetail(accountId))
        }
    }
}
