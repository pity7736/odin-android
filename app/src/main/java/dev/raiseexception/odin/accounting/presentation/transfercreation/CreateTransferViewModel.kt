package dev.raiseexception.odin.accounting.presentation.transfercreation

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.Transfer
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.amountInputToRaw
import dev.raiseexception.odin.shared.presentation.gmfTaxLineFor
import dev.raiseexception.odin.shared.presentation.isMoneyAccount
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

class CreateTransferViewModel(
    private val originAccountId: String?,
    private val transferCreator: TransferCreator,
    private val accountLister: AccountLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateTransferUiState>(CreateTransferUiState.Loading)
    val uiState: StateFlow<CreateTransferUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private var accounts: List<Account> = emptyList()
    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = CreateTransferUiState.Loading
        this.restartableLoad.start()
    }

    fun onSourceSelected(sourceAccountId: String) {
        val form = this.selectSource(this.currentForm(), sourceAccountId)
        when (val current = this.mutableUiState.value) {
            is CreateTransferUiState.Idle -> this.mutableUiState.value = this.idle(form)
            is CreateTransferUiState.ValidationError -> {
                this.mutableUiState.value = this.validationError(form, current).copy(sourceAccountError = null)
            }
            else -> Unit
        }
    }

    fun onDestinationSelected(destinationAccountId: String) {
        val form = this.currentForm().copy(destinationAccountId = destinationAccountId)
        when (val current = this.mutableUiState.value) {
            is CreateTransferUiState.Idle -> this.mutableUiState.value = this.idle(form)
            is CreateTransferUiState.ValidationError -> {
                this.mutableUiState.value = this.validationError(form, current).copy(destinationAccountError = null)
            }
            else -> Unit
        }
    }

    fun onAmountChanged(amount: String) {
        this.updateForm { form -> form.copy(amount = amount) }
    }

    fun onChargeGmfChanged(chargeGmf: Boolean) {
        this.updateForm { form -> form.copy(chargeGmf = chargeGmf) }
    }

    fun save(date: String) {
        val stateBeforeSaving = this.mutableUiState.value
        if (stateBeforeSaving is CreateTransferUiState.Saving) return
        val form = this.currentForm()
        val formState = this.idle(form)
        this.mutableUiState.value = CreateTransferUiState.Saving(
            sourceAccounts = formState.sourceAccounts,
            destinationAccounts = formState.destinationAccounts,
            selectedSourceAccountId = formState.selectedSourceAccountId,
            selectedDestinationAccountId = formState.selectedDestinationAccountId,
            saveLabel = formState.saveLabel,
            amount = formState.amount,
            chargeGmf = formState.chargeGmf,
            showsGmfOption = formState.showsGmfOption,
            gmfTaxLine = formState.gmfTaxLine
        )
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = {
                withContext(this.ioDispatcher) {
                    transferCreator.create(
                        sourceAccountId = form.sourceAccountId,
                        destinationAccountId = form.destinationAccountId,
                        amount = amountInputToRaw(form.amount),
                        date = date,
                        chargeGmf = form.chargeGmf && formState.showsGmfOption
                    )
                }
            },
            onResult = { outcome -> this.handleSave(outcome, form) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    private fun updateForm(update: (TransferForm) -> TransferForm) {
        val form = update(this.currentForm())
        when (val current = this.mutableUiState.value) {
            is CreateTransferUiState.Idle -> this.mutableUiState.value = this.idle(form)
            is CreateTransferUiState.ValidationError -> this.mutableUiState.value = this.validationError(form, current)
            else -> Unit
        }
    }

    private suspend fun handleSave(outcome: Outcome<Transfer>, form: TransferForm) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(NavigationTarget.AccountDetail(form.sourceAccountId))
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(outcome.error, form)
        }
    }

    private fun load(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(accountLister.list().first()) }.flowOn(this.ioDispatcher),
            onEach = { outcome ->
                this.mutableUiState.value = when (outcome) {
                    is Outcome.Success -> {
                        this.accounts = outcome.value
                        this.initialState()
                    }
                    is Outcome.Failure -> CreateTransferUiState.Error(outcome.error.externalMessage)
                }
            },
            onUnexpectedFailure = { this.mutableUiState.value = CreateTransferUiState.UnexpectedFailure }
        )

    private fun initialState(): CreateTransferUiState.Idle {
        val origin = this.accounts.firstOrNull { it.id == this.originAccountId }
        val form = when (origin?.funding) {
            is AccountFunding.Funds -> TransferForm(sourceAccountId = origin.id, destinationAccountId = "")
            is AccountFunding.Credit -> TransferForm(sourceAccountId = "", destinationAccountId = origin.id)
            null -> TransferForm(sourceAccountId = "", destinationAccountId = "")
        }
        return this.idle(form)
    }

    private fun selectSource(form: TransferForm, sourceAccountId: String): TransferForm = form.copy(
        sourceAccountId = sourceAccountId,
        destinationAccountId = if (form.destinationAccountId == sourceAccountId) "" else form.destinationAccountId,
        chargeGmf = true
    )

    private fun idle(form: TransferForm): CreateTransferUiState.Idle {
        val source = this.accounts.firstOrNull { it.id == form.sourceAccountId }
        val showsGmfOption = source?.funding?.gmfTreatment is GmfTreatment.Taxed
        return CreateTransferUiState.Idle(
            sourceAccounts = this.accounts.filter { isMoneyAccount(it) },
            destinationAccounts = this.destinationAccountsFor(form.sourceAccountId),
            selectedSourceAccountId = form.sourceAccountId,
            selectedDestinationAccountId = form.destinationAccountId,
            saveLabel = this.saveLabelFor(form.destinationAccountId),
            amount = form.amount,
            chargeGmf = form.chargeGmf,
            showsGmfOption = showsGmfOption,
            gmfTaxLine = if (showsGmfOption && form.chargeGmf) gmfTaxLineFor(source, form.amount) else null
        )
    }

    private fun validationError(
        form: TransferForm,
        current: CreateTransferUiState.ValidationError
    ): CreateTransferUiState.ValidationError {
        val formState = this.idle(form)
        return current.copy(
            destinationAccounts = formState.destinationAccounts,
            selectedSourceAccountId = formState.selectedSourceAccountId,
            selectedDestinationAccountId = formState.selectedDestinationAccountId,
            saveLabel = formState.saveLabel,
            amount = formState.amount,
            chargeGmf = formState.chargeGmf,
            showsGmfOption = formState.showsGmfOption,
            gmfTaxLine = formState.gmfTaxLine
        )
    }

    private fun destinationAccountsFor(sourceAccountId: String): List<Account> =
        this.accounts.filter { it.id != sourceAccountId }

    private fun saveLabelFor(destinationAccountId: String): String =
        when (this.accounts.firstOrNull { it.id == destinationAccountId }?.funding) {
            is AccountFunding.Credit -> PAY_LABEL
            is AccountFunding.Funds, null -> TRANSFER_LABEL
        }

    private fun mapError(error: DomainError, form: TransferForm): CreateTransferUiState = when (error) {
        is TransferCreationError.InvalidInput -> {
            val formState = this.idle(form)
            CreateTransferUiState.ValidationError(
                sourceAccounts = formState.sourceAccounts,
                destinationAccounts = formState.destinationAccounts,
                selectedSourceAccountId = formState.selectedSourceAccountId,
                selectedDestinationAccountId = formState.selectedDestinationAccountId,
                saveLabel = formState.saveLabel,
                amount = formState.amount,
                chargeGmf = formState.chargeGmf,
                showsGmfOption = formState.showsGmfOption,
                gmfTaxLine = formState.gmfTaxLine,
                amountError = error.amountError,
                dateError = error.dateError,
                sourceAccountError = error.sourceAccountError,
                destinationAccountError = error.destinationAccountError
            )
        }
        else -> CreateTransferUiState.Error(error.externalMessage)
    }

    private fun currentForm(): TransferForm = when (val current = this.mutableUiState.value) {
        is CreateTransferUiState.Idle -> TransferForm(
            sourceAccountId = current.selectedSourceAccountId,
            destinationAccountId = current.selectedDestinationAccountId,
            amount = current.amount,
            chargeGmf = current.chargeGmf
        )
        is CreateTransferUiState.ValidationError -> TransferForm(
            sourceAccountId = current.selectedSourceAccountId,
            destinationAccountId = current.selectedDestinationAccountId,
            amount = current.amount,
            chargeGmf = current.chargeGmf
        )
        else -> TransferForm(sourceAccountId = "", destinationAccountId = "")
    }

    private data class TransferForm(
        val sourceAccountId: String,
        val destinationAccountId: String,
        val amount: String = "",
        val chargeGmf: Boolean = true
    )

    companion object {
        private const val LOAD_OPERATION = "transfer-creation.load"
        private const val SUBMIT_OPERATION = "transfer-creation.submit"
        private const val PAY_LABEL = "Pagar"
        private const val TRANSFER_LABEL = "Transferir"
    }
}
