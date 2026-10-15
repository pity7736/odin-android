package dev.raiseexception.odin.accounting.presentation.accountslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.launchObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AccountsListViewModel(
    private val accountLister: AccountLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<AccountsListUiState>(AccountsListUiState.Loading)
    val uiState: StateFlow<AccountsListUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<AccountsListNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<AccountsListNavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = AccountsListUiState.Loading
        this.restartableLoad.start()
    }

    fun onAccountSelected(accountId: String) {
        this.viewModelScope.launch {
            this@AccountsListViewModel.navigationChannel.send(AccountsListNavigationTarget.AccountDetail(accountId))
        }
    }

    private fun load(): Job {
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        return this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = this.accountLister.list(
                criteria
            ).map { outcome -> this.toUiState(outcome) }.flowOn(this.ioDispatcher),
            onEach = { state -> this.mutableUiState.value = state },
            onUnexpectedFailure = { this.mutableUiState.value = AccountsListUiState.UnexpectedFailure }
        )
    }

    private fun toUiState(outcome: Outcome<List<Account>>): AccountsListUiState = when (outcome) {
        is Outcome.Success -> this.groupAccounts(outcome.value)
        is Outcome.Failure -> AccountsListUiState.Error("Error al cargar las cuentas")
    }

    private fun groupAccounts(accounts: List<Account>): AccountsListUiState {
        val moneyAccounts = accounts.filter { it.funding is AccountFunding.Funds }
        val creditCards = accounts.mapNotNull { account -> this.toCreditCardItem(account) }
        return if (moneyAccounts.isEmpty() && creditCards.isEmpty()) {
            AccountsListUiState.Empty
        } else {
            AccountsListUiState.Content(moneyAccounts = moneyAccounts, creditCards = creditCards)
        }
    }

    private fun toCreditCardItem(account: Account): CreditCardItem? = when (val funding = account.funding) {
        is AccountFunding.Credit -> CreditCardItem(
            id = account.id,
            name = account.name,
            debt = funding.currentDebt(account.incomes, account.expenses),
            availableCredit = funding.availableCredit(account.incomes, account.expenses)
        )
        is AccountFunding.Funds -> null
    }

    private companion object {
        const val LOAD_OPERATION = "accounts-list.load"
    }
}
