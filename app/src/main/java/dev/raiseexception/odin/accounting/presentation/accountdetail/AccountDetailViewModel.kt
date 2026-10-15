package dev.raiseexception.odin.accounting.presentation.accountdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.AccountTransactionLister
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.isMoneyAccount
import dev.raiseexception.odin.shared.presentation.launchObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AccountDetailViewModel(
    private val accountId: String,
    private val accountFinder: AccountFinder,
    private val accountLister: AccountLister,
    private val accountTransactionLister: AccountTransactionLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<AccountDetailUiState>(AccountDetailUiState.Loading)
    val uiState: StateFlow<AccountDetailUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<AccountDetailNavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<AccountDetailNavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val activeFilter = MutableStateFlow(TransactionFilter.ALL)
    private var cachedDetail: LoadedDetail? = null
    private val restartableLoad = RestartableLoad { this.observeAccount() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = AccountDetailUiState.Loading
        this.restartableLoad.start()
    }

    private fun observeAccount(): Job {
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        val detailFlow = combine(
            this.accountFinder.find(this.accountId, criteria),
            this.accountLister.list()
        ) { accountOutcome, accountsOutcome ->
            this.toLoadedState(accountOutcome, this.hasMoneyAccount(accountsOutcome))
        }.flowOn(this.ioDispatcher)
        return this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = detailFlow,
            onEach = { (detail, state) ->
                this.cachedDetail = detail ?: this.cachedDetail
                this.mutableUiState.value = state
            },
            onUnexpectedFailure = { this.mutableUiState.value = AccountDetailUiState.UnexpectedFailure }
        )
    }

    private fun toLoadedState(outcome: Outcome<Account>, canPay: Boolean): LoadedState = when (outcome) {
        is Outcome.Success -> {
            val detail = LoadedDetail(outcome.value, canPay)
            LoadedState(detail, this.toContentState(detail, this.activeFilter.value))
        }
        is Outcome.Failure -> when (outcome.error) {
            is AccountLookupError.NotFound -> LoadedState(null, AccountDetailUiState.NotFound)
            else -> LoadedState(null, AccountDetailUiState.Error(outcome.error.externalMessage))
        }
    }

    fun onFilterChanged(filter: TransactionFilter) {
        this.activeFilter.value = filter
        val detail = this.cachedDetail ?: return
        this.mutableUiState.value = this.toContentState(detail, filter)
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

    fun onEditAccount() {
        this.viewModelScope.launch {
            this@AccountDetailViewModel.navigationChannel.send(
                AccountDetailNavigationTarget.EditAccount(this@AccountDetailViewModel.accountId)
            )
        }
    }

    fun onTransactionSelected(transactionId: String) {
        this.viewModelScope.launch {
            this@AccountDetailViewModel.navigationChannel.send(
                AccountDetailNavigationTarget.TransactionDetail(transactionId)
            )
        }
    }

    private fun hasMoneyAccount(accountsOutcome: Outcome<List<Account>>): Boolean = when (accountsOutcome) {
        is Outcome.Success -> accountsOutcome.value.any { isMoneyAccount(it) }
        is Outcome.Failure -> false
    }

    private fun toContentState(detail: LoadedDetail, filter: TransactionFilter): AccountDetailUiState {
        val account = detail.account
        return when (val funding = account.funding) {
            is AccountFunding.Funds -> AccountDetailUiState.MoneyAccountContent(
                account = account,
                initialBalance = funding.initialBalance,
                transactions = this.accountTransactionLister.list(account, filter),
                activeFilter = filter,
            )
            is AccountFunding.Credit -> AccountDetailUiState.CreditCardContent(
                creditCard = CreditCardDetail(
                    name = account.name,
                    debt = funding.currentDebt(account.incomes, account.expenses),
                    availableCredit = funding.availableCredit(account.incomes, account.expenses),
                    creditLimit = funding.creditLimit
                ),
                canPay = detail.canPay,
                transactions = this.accountTransactionLister.list(account, filter),
                activeFilter = filter,
            )
        }
    }

    private data class LoadedDetail(val account: Account, val canPay: Boolean)

    private data class LoadedState(val detail: LoadedDetail?, val uiState: AccountDetailUiState)

    private companion object {
        const val LOAD_OPERATION = "account-detail.load"
    }
}
