package dev.raiseexception.odin.accounting.presentation.transfercreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CreateTransferViewModel(
    private val preselectedSourceAccountId: String?,
    private val transferCreator: TransferCreator,
    private val accountLister: AccountLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateTransferUiState>(CreateTransferUiState.Loading)
    val uiState: StateFlow<CreateTransferUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateTransferViewModel.accountLister.list().first()
            this@CreateTransferViewModel.mutableUiState.value = when (outcome) {
                is Outcome.Success -> CreateTransferUiState.Idle(
                    accounts = outcome.value,
                    selectedSourceAccountId = this@CreateTransferViewModel.preselectedSourceAccountId ?: ""
                )
                is Outcome.Failure -> CreateTransferUiState.Error(outcome.error.externalMessage)
            }
        }
    }

    fun save(sourceAccountId: String, destinationAccountId: String, amount: String, date: String) {
        if (this.mutableUiState.value is CreateTransferUiState.Saving) return
        val accounts = currentAccounts()
        this.mutableUiState.value = CreateTransferUiState.Saving
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateTransferViewModel.transferCreator.create(
                sourceAccountId = sourceAccountId,
                destinationAccountId = destinationAccountId,
                amount = amount,
                date = date
            )
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(
                    NavigationTarget.AccountDetail(sourceAccountId)
                )
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error, accounts)
            }
        }
    }

    private fun mapError(error: DomainError, accounts: List<Account>): CreateTransferUiState {
        return when (error) {
            is TransferCreationError.InvalidInput -> CreateTransferUiState.ValidationError(
                accounts = accounts,
                amountError = error.amountError,
                dateError = error.dateError,
                sourceAccountError = error.sourceAccountError,
                destinationAccountError = error.destinationAccountError
            )
            else -> CreateTransferUiState.Error(error.externalMessage)
        }
    }

    private fun currentAccounts() = when (val current = this.mutableUiState.value) {
        is CreateTransferUiState.Idle -> current.accounts
        is CreateTransferUiState.ValidationError -> current.accounts
        is CreateTransferUiState.Saving -> emptyList()
        else -> emptyList()
    }
}
