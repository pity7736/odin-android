package dev.raiseexception.odin.home.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.home.application.usecase.HomeSummary
import dev.raiseexception.odin.home.application.usecase.HomeSummaryLoader
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val homeSummaryLoader: HomeSummaryLoader,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<HomeNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<HomeNavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.load()
    }

    private fun load() {
        this.viewModelScope.launch(this.ioDispatcher) {
            this@HomeViewModel.homeSummaryLoader.load().collect { outcome ->
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

    fun onIncomeShortcutSelected() {
        this.viewModelScope.launch {
            this@HomeViewModel.navigationChannel.send(HomeNavigationTarget.IncomeCreate)
        }
    }

    fun onExpenseShortcutSelected() {
        this.viewModelScope.launch {
            this@HomeViewModel.navigationChannel.send(HomeNavigationTarget.ExpenseCreate)
        }
    }

    fun onTransferShortcutSelected() {
        this.viewModelScope.launch {
            this@HomeViewModel.navigationChannel.send(HomeNavigationTarget.TransferCreate)
        }
    }

    private fun mapToUiState(summary: HomeSummary): HomeUiState =
        if (summary.entries.isEmpty()) {
            HomeUiState.Empty
        } else {
            HomeUiState.Content(
                balanceTotals = summary.balanceTotals,
                debtTotals = summary.debtTotals,
                accounts = summary.entries,
                hasMoreAccounts = summary.hasMoreEntries,
                recentTransactions = summary.recentTransactions,
                canTransfer = summary.canTransfer,
                canRecordIncome = summary.canRecordIncome,
            )
        }
}
