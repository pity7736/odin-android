package dev.raiseexception.odin.accounting.presentation.transfercreation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.isMoneyAccount
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
    private val originAccountId: String?,
    private val transferCreator: TransferCreator,
    private val accountLister: AccountLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateTransferUiState>(CreateTransferUiState.Loading)
    val uiState: StateFlow<CreateTransferUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private var accounts: List<Account> = emptyList()

    init {
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateTransferViewModel.accountLister.list().first()
            this@CreateTransferViewModel.mutableUiState.value = when (outcome) {
                is Outcome.Success -> {
                    this@CreateTransferViewModel.accounts = outcome.value
                    this@CreateTransferViewModel.initialState()
                }
                is Outcome.Failure -> CreateTransferUiState.Error(outcome.error.externalMessage)
            }
        }
    }

    fun onSourceSelected(sourceAccountId: String) {
        when (val current = this.mutableUiState.value) {
            is CreateTransferUiState.Idle -> {
                val selection = this.selectSource(this.currentSelection(), sourceAccountId)
                this.mutableUiState.value = this.idle(selection)
            }
            is CreateTransferUiState.ValidationError -> {
                val selection = this.selectSource(this.currentSelection(), sourceAccountId)
                this.mutableUiState.value = current.copy(
                    destinationAccounts = this.destinationAccountsFor(selection.sourceAccountId),
                    selectedSourceAccountId = selection.sourceAccountId,
                    selectedDestinationAccountId = selection.destinationAccountId,
                    saveLabel = this.saveLabelFor(selection.destinationAccountId),
                    sourceAccountError = null
                )
            }
            else -> Unit
        }
    }

    fun onDestinationSelected(destinationAccountId: String) {
        when (val current = this.mutableUiState.value) {
            is CreateTransferUiState.Idle -> this.mutableUiState.value = current.copy(
                selectedDestinationAccountId = destinationAccountId,
                saveLabel = this.saveLabelFor(destinationAccountId)
            )
            is CreateTransferUiState.ValidationError -> this.mutableUiState.value = current.copy(
                selectedDestinationAccountId = destinationAccountId,
                saveLabel = this.saveLabelFor(destinationAccountId),
                destinationAccountError = null
            )
            else -> Unit
        }
    }

    fun save(amount: String, date: String) {
        if (this.mutableUiState.value is CreateTransferUiState.Saving) return
        val selection = this.currentSelection()
        val formState = this.idle(selection)
        this.mutableUiState.value = CreateTransferUiState.Saving(
            sourceAccounts = formState.sourceAccounts,
            destinationAccounts = formState.destinationAccounts,
            selectedSourceAccountId = formState.selectedSourceAccountId,
            selectedDestinationAccountId = formState.selectedDestinationAccountId,
            saveLabel = formState.saveLabel
        )
        this.viewModelScope.launch(this.ioDispatcher) {
            val outcome = this@CreateTransferViewModel.transferCreator.create(
                sourceAccountId = selection.sourceAccountId,
                destinationAccountId = selection.destinationAccountId,
                amount = amount,
                date = date
            )
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(NavigationTarget.AccountDetail(selection.sourceAccountId))
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error, selection)
            }
        }
    }

    private fun initialState(): CreateTransferUiState.Idle {
        val origin = this.accounts.firstOrNull { it.id == this.originAccountId }
        val selection = when (origin?.funding) {
            is AccountFunding.Funds -> Selection(sourceAccountId = origin.id, destinationAccountId = "")
            is AccountFunding.Credit -> Selection(sourceAccountId = "", destinationAccountId = origin.id)
            null -> Selection(sourceAccountId = "", destinationAccountId = "")
        }
        return this.idle(selection)
    }

    private fun selectSource(selection: Selection, sourceAccountId: String): Selection = Selection(
        sourceAccountId = sourceAccountId,
        destinationAccountId = if (selection.destinationAccountId == sourceAccountId) {
            ""
        } else {
            selection.destinationAccountId
        }
    )

    private fun idle(selection: Selection): CreateTransferUiState.Idle = CreateTransferUiState.Idle(
        sourceAccounts = this.accounts.filter { isMoneyAccount(it) },
        destinationAccounts = this.destinationAccountsFor(selection.sourceAccountId),
        selectedSourceAccountId = selection.sourceAccountId,
        selectedDestinationAccountId = selection.destinationAccountId,
        saveLabel = this.saveLabelFor(selection.destinationAccountId)
    )

    private fun destinationAccountsFor(sourceAccountId: String): List<Account> =
        this.accounts.filter { it.id != sourceAccountId }

    private fun saveLabelFor(destinationAccountId: String): String =
        when (this.accounts.firstOrNull { it.id == destinationAccountId }?.funding) {
            is AccountFunding.Credit -> PAY_LABEL
            is AccountFunding.Funds, null -> TRANSFER_LABEL
        }

    private fun mapError(error: DomainError, selection: Selection): CreateTransferUiState = when (error) {
        is TransferCreationError.InvalidInput -> {
            val formState = this.idle(selection)
            CreateTransferUiState.ValidationError(
                sourceAccounts = formState.sourceAccounts,
                destinationAccounts = formState.destinationAccounts,
                selectedSourceAccountId = formState.selectedSourceAccountId,
                selectedDestinationAccountId = formState.selectedDestinationAccountId,
                saveLabel = formState.saveLabel,
                amountError = error.amountError,
                dateError = error.dateError,
                sourceAccountError = error.sourceAccountError,
                destinationAccountError = error.destinationAccountError
            )
        }
        else -> CreateTransferUiState.Error(error.externalMessage)
    }

    private fun currentSelection(): Selection = when (val current = this.mutableUiState.value) {
        is CreateTransferUiState.Idle -> Selection(
            sourceAccountId = current.selectedSourceAccountId,
            destinationAccountId = current.selectedDestinationAccountId
        )
        is CreateTransferUiState.ValidationError -> Selection(
            sourceAccountId = current.selectedSourceAccountId,
            destinationAccountId = current.selectedDestinationAccountId
        )
        else -> Selection(sourceAccountId = "", destinationAccountId = "")
    }

    private data class Selection(val sourceAccountId: String, val destinationAccountId: String)

    companion object {
        private const val PAY_LABEL = "Pagar"
        private const val TRANSFER_LABEL = "Transferir"
    }
}
