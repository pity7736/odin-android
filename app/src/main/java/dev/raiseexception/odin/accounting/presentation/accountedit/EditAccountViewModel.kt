package dev.raiseexception.odin.accounting.presentation.accountedit

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountUpdater
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.shared.presentation.launchObservation
import dev.raiseexception.odin.shared.presentation.launchOperation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

class EditAccountViewModel(
    private val accountId: String,
    private val accountFinder: AccountFinder,
    private val accountUpdater: AccountUpdater,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<EditAccountUiState>(EditAccountUiState.Loading)
    val uiState: StateFlow<EditAccountUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent: Flow<Unit> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = EditAccountUiState.Loading
        this.restartableLoad.start()
    }

    @Suppress("LongParameterList")
    fun save(
        rawName: String,
        rawBalance: String,
        currency: Currency?,
        kind: MoneyAccountKind?,
        rawDescription: String
    ) {
        val current = this.mutableUiState.value
        if (current !is EditAccountUiState.Editing || current.isSaving) return
        this.mutableUiState.value = current.copy(
            isSaving = true,
            nameError = null,
            balanceError = null,
            currencyError = null,
            typeError = null,
            descriptionError = null,
            saveError = null
        )
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = {
                withContext(this.ioDispatcher) {
                    accountUpdater.update(accountId, rawName, rawBalance, currency, kind, rawDescription)
                }
            },
            onResult = { outcome -> this.handleSave(outcome) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(current) }
        )
    }

    private suspend fun handleSave(outcome: Outcome<Account>) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(Unit)
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(this.savingState(), outcome.error)
        }
    }

    private fun load(): Job {
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        return this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(accountFinder.find(accountId, criteria).first()) }.flowOn(this.ioDispatcher),
            onEach = { outcome ->
                this.mutableUiState.value = when (outcome) {
                    is Outcome.Success -> this.buildEditing(outcome.value)
                    is Outcome.Failure -> EditAccountUiState.NotFound
                }
            },
            onUnexpectedFailure = { this.mutableUiState.value = EditAccountUiState.UnexpectedFailure }
        )
    }

    private fun buildEditing(account: Account): EditAccountUiState.Editing {
        val locked = account.hasTransactions()
        val (kind, initialBalance) = when (val funding = account.funding) {
            is AccountFunding.Funds -> Pair(funding.kind, funding.initialBalance)
            is AccountFunding.Credit -> Pair(null, funding.creditLimit)
        }
        return EditAccountUiState.Editing(
            name = account.name,
            initialBalance = initialBalance.amount.toPlainString(),
            currency = account.currency,
            kind = kind,
            description = account.description,
            locked = locked,
            lockedBalanceDisplay = if (locked) formatMoney(initialBalance) else null
        )
    }

    private fun savingState(): EditAccountUiState.Editing =
        this.mutableUiState.value as EditAccountUiState.Editing

    private fun mapError(editing: EditAccountUiState.Editing, error: DomainError): EditAccountUiState =
        when (error) {
            is AccountUpdateError.InvalidInput -> editing.copy(
                isSaving = false,
                nameError = error.nameError,
                balanceError = error.balanceError,
                currencyError = error.currencyError,
                typeError = error.typeError,
                descriptionError = error.descriptionError
            )
            is AccountUpdateError.DuplicateName -> editing.copy(isSaving = false, nameError = error.externalMessage)
            is AccountUpdateError.StorageFailure -> editing.copy(isSaving = false, saveError = error.externalMessage)
            is AccountUpdateError.CreditCardNotEditable ->
                editing.copy(isSaving = false, saveError = error.externalMessage)
            else -> editing.copy(isSaving = false, saveError = error.externalMessage)
        }

    private companion object {
        const val LOAD_OPERATION = "account-edit.load"
        const val SUBMIT_OPERATION = "account-edit.submit"
    }
}
