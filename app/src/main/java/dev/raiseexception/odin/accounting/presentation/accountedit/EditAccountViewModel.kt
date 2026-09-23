package dev.raiseexception.odin.accounting.presentation.accountedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountUpdater
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.formatMoney
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditAccountViewModel(
    private val accountId: String,
    private val accountFinder: AccountFinder,
    private val accountUpdater: AccountUpdater,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<EditAccountUiState>(EditAccountUiState.Loading)
    val uiState: StateFlow<EditAccountUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent: Flow<Unit> = this.navigationChannel.receiveAsFlow()

    init {
        this.load()
    }

    @Suppress("LongParameterList")
    fun save(
        rawName: String,
        rawBalance: String,
        currency: Currency?,
        type: AccountType?,
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
        this.viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                accountUpdater.update(accountId, rawName, rawBalance, currency, type, rawDescription)
            }
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(Unit)
                is Outcome.Failure -> mutableUiState.value = mapError(savingState(), outcome.error)
            }
        }
    }

    private fun load() {
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        this.viewModelScope.launch {
            val outcome = accountFinder.find(accountId, criteria).flowOn(ioDispatcher).first()
            mutableUiState.value = when (outcome) {
                is Outcome.Success -> buildEditing(outcome.value)
                is Outcome.Failure -> EditAccountUiState.NotFound
            }
        }
    }

    private fun buildEditing(account: Account): EditAccountUiState.Editing {
        val locked = account.hasTransactions()
        return EditAccountUiState.Editing(
            name = account.name,
            initialBalance = account.initialBalance.amount.toPlainString(),
            currency = account.currency,
            type = account.type,
            description = account.description,
            locked = locked,
            lockedBalanceDisplay = if (locked) formatMoney(account.initialBalance) else null
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
            else -> editing.copy(isSaving = false, saveError = error.externalMessage)
        }
}
