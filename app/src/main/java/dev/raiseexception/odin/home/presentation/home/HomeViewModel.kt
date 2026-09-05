package dev.raiseexception.odin.home.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.home.application.usecase.RecentTransactionLister
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val MAX_DISPLAYED_ACCOUNTS = 3

class HomeViewModel(
    private val accountLister: AccountLister,
    private val recentTransactionLister: RecentTransactionLister,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<HomeNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<HomeNavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.load()
    }

    fun reload() {
        this.load()
    }

    private fun load() {
        this.viewModelScope.launch(this.ioDispatcher) {
            val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
            this@HomeViewModel.accountLister.list(criteria).collect { outcome ->
                this@HomeViewModel.mutableUiState.value = when (outcome) {
                    is Outcome.Success -> this@HomeViewModel.mapToUiState(outcome.value)
                    is Outcome.Failure -> HomeUiState.Error("Error al cargar la información")
                }
            }
        }
    }

    fun onAccountSelected(accountId: String) {
        this.viewModelScope.launch {
            this@HomeViewModel.navigationChannel.send(HomeNavigationTarget.AccountDetail(accountId))
        }
    }

    fun onTransactionSelected(transactionId: String) {
        this.viewModelScope.launch {
            this@HomeViewModel.navigationChannel.send(HomeNavigationTarget.TransactionDetail(transactionId))
        }
    }

    fun onCreateAccountSelected() {
        this.viewModelScope.launch {
            this@HomeViewModel.navigationChannel.send(HomeNavigationTarget.AccountCreate)
        }
    }

    private fun mapToUiState(accounts: List<Account>): HomeUiState {
        if (accounts.isEmpty()) {
            return HomeUiState.Empty
        }
        val totalBalances = this.computeTotalBalances(accounts)
        val displayedAccounts = accounts.take(MAX_DISPLAYED_ACCOUNTS)
        val hasMoreAccounts = accounts.size > MAX_DISPLAYED_ACCOUNTS
        val recentTransactions = this.recentTransactionLister.list(accounts)
        return HomeUiState.Content(
            totalBalances = totalBalances,
            accounts = displayedAccounts,
            hasMoreAccounts = hasMoreAccounts,
            recentTransactions = recentTransactions,
        )
    }

    private fun computeTotalBalances(accounts: List<Account>): List<Money> =
        accounts
            .groupBy { it.currency }
            .map { (currency, currencyAccounts) ->
                val totalAmount = currencyAccounts.fold(BigDecimal.ZERO) { acc, account ->
                    acc.add(account.balance.amount)
                }
                Money.of(totalAmount, currency)
            }
}
