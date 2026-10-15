package dev.raiseexception.odin.accounts.presentation.registration

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.crypto.domain.SensitivePassword
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

class RegistrationViewModel(
    private val userRegistrar: UserRegistrar,
    private val failureRecorder: FailureRecorder,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Idle)
    val uiState: StateFlow<RegistrationUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    fun register(rawPassword: String, rawPasswordConfirmation: String) {
        val stateBeforeSaving = this.mutableUiState.value
        if (stateBeforeSaving is RegistrationUiState.Loading) return
        this.mutableUiState.value = RegistrationUiState.Loading
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = {
                this.userRegistrar.register(
                    SensitivePassword(rawPassword.toCharArray()),
                    SensitivePassword(rawPasswordConfirmation.toCharArray())
                )
            },
            onResult = { outcome -> this.handle(outcome) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    private suspend fun handle(outcome: Outcome<User>) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(NavigationTarget.Home)
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(outcome.error)
        }
    }

    private fun mapError(error: DomainError): RegistrationUiState = when (error) {
        is RegistrationError.InvalidPassword -> RegistrationUiState.ValidationError(
            passwordError = error.externalMessage
        )
        is RegistrationError.PasswordsDoNotMatch -> RegistrationUiState.ValidationError(
            passwordConfirmationError = error.externalMessage
        )
        is RegistrationError.CryptoFailure -> RegistrationUiState.Error(error.externalMessage)
        is RegistrationError.StorageFailure -> RegistrationUiState.Error(error.externalMessage)
        is RegistrationError.AlreadyRegistered -> RegistrationUiState.Error(error.externalMessage)
        else -> RegistrationUiState.Error(error.externalMessage)
    }

    private companion object {
        const val SUBMIT_OPERATION = "registration.submit"
    }
}
