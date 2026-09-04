package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountTransactionLister
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
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

class AccountDetailViewModel(
    private val accountId: String,
    private val accountFinder: AccountFinder,
    private val accountTransactionLister: AccountTransactionLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<AccountDetailUiState>(AccountDetailUiState.Loading)
    val uiState: StateFlow<AccountDetailUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<AccountDetailNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<AccountDetailNavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val activeFilter = MutableStateFlow(TransactionFilter.ALL)
    private var cachedAccount: Account? = null

    init {
        this.load()
    }

    fun reload() {
        this.load()
    }

    private fun load() {
        this.viewModelScope.launch(this.ioDispatcher) {
            this@AccountDetailViewModel.mutableUiState.value = when (
                val outcome = this@AccountDetailViewModel.accountFinder.find(
                    this@AccountDetailViewModel.accountId,
                    AccountCriteria(includeIncomes = true, includeExpenses = true)
                )
            ) {
                is Outcome.Success -> {
                    this@AccountDetailViewModel.cachedAccount = outcome.value
                    this@AccountDetailViewModel.buildContentState(
                        outcome.value,
                        this@AccountDetailViewModel.activeFilter.value
                    )
                }

                is Outcome.Failure -> when (outcome.error) {
                    is AccountLookupError.NotFound -> AccountDetailUiState.NotFound
                    else -> AccountDetailUiState.Error(outcome.error.externalMessage)
                }
            }
        }
    }

    fun onFilterChanged(filter: TransactionFilter) {
        this.activeFilter.value = filter
        val account = this.cachedAccount ?: return
        this.mutableUiState.value = this.buildContentState(account, filter)
    }

    fun onCreateIncome() {
        this.viewModelScope.launch {
            this@AccountDetailViewModel.navigationChannel.send(
                AccountDetailNavigationTarget.CreateIncome(this@AccountDetailViewModel.accountId)
            )
        }
    }

    fun onCreateExpense() {
        this.viewModelScope.launch {
            this@AccountDetailViewModel.navigationChannel.send(
                AccountDetailNavigationTarget.CreateExpense(this@AccountDetailViewModel.accountId)
            )
        }
    }

    private fun buildContentState(account: Account, filter: TransactionFilter): AccountDetailUiState.Content {
        val transactions = this.accountTransactionLister.list(
            transactions = account.transactions,
            currentBalance = account.balance,
            filter = filter
        )
        return AccountDetailUiState.Content(
            account = account,
            transactions = transactions,
            activeFilter = filter,
        )
    }
}
