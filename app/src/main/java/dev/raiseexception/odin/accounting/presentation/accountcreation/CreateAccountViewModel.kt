package dev.raiseexception.odin.accounting.presentation.accountcreation

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.CreateAccountCommand
import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.launchOperation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class CreateAccountViewModel(
    private val accountCreator: AccountCreator,
    private val failureRecorder: FailureRecorder,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateAccountUiState>(CreateAccountUiState.Idle)
    val uiState: StateFlow<CreateAccountUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    fun create(command: CreateAccountCommand) {
        val stateBeforeSaving = this.mutableUiState.value
        if (stateBeforeSaving is CreateAccountUiState.Loading) return
        this.mutableUiState.value = CreateAccountUiState.Loading
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = { this.accountCreator.create(command) },
            onResult = { outcome -> this.handle(outcome) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    private suspend fun handle(outcome: Outcome<Account>) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(NavigationTarget.AccountsList)
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(outcome.error)
        }
    }

    private fun mapError(error: DomainError): CreateAccountUiState = when (error) {
        is AccountCreationError.InvalidInput -> CreateAccountUiState.ValidationError(
            nameError = error.nameError,
            balanceError = error.balanceError,
            currencyError = error.currencyError,
            typeError = error.typeError,
            descriptionError = error.descriptionError,
            creditLimitError = error.creditLimitError,
            debtError = error.debtError
        )

        is AccountCreationError.DuplicateName -> CreateAccountUiState.ValidationError(
            nameError = error.externalMessage
        )

        is AccountCreationError.CryptoFailure -> CreateAccountUiState.Error(error.externalMessage)
        is AccountCreationError.StorageFailure -> CreateAccountUiState.Error(error.externalMessage)
        else -> CreateAccountUiState.Error(error.externalMessage)
    }

    private companion object {
        const val SUBMIT_OPERATION = "account-creation.submit"
    }
}
