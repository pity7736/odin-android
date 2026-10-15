package dev.raiseexception.odin.accounts.presentation.login

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.domain.LoginError
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

class LoginViewModel(
    private val userAuthenticator: UserAuthenticator,
    private val failureRecorder: FailureRecorder,
    private val onLoginSuccess: suspend () -> Unit = {},
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    fun login(rawPassword: String) {
        val stateBeforeSaving = this.mutableUiState.value
        if (stateBeforeSaving is LoginUiState.Loading) return
        this.mutableUiState.value = LoginUiState.Loading
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = { this.userAuthenticator.authenticate(SensitivePassword(rawPassword.toCharArray())) },
            onResult = { outcome -> this.handle(outcome) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    private suspend fun handle(outcome: Outcome<User>) {
        when (outcome) {
            is Outcome.Success -> {
                this.onLoginSuccess()
                this.navigationChannel.send(NavigationTarget.Home)
            }
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(outcome.error)
        }
    }

    private fun mapError(error: DomainError): LoginUiState = when (error) {
        is LoginError.EmptyPassword -> LoginUiState.ValidationError(passwordError = error.externalMessage)
        is LoginError.InvalidCredentials -> LoginUiState.Error(error.externalMessage)
        is LoginError.CryptoFailure -> LoginUiState.Error(error.externalMessage)
        is LoginError.UserNotFound -> LoginUiState.Error(error.externalMessage)
        else -> LoginUiState.Error(error.externalMessage)
    }

    private companion object {
        const val SUBMIT_OPERATION = "login.submit"
    }
}
