package dev.raiseexception.odin.accounts.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.domain.LoginError
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val userAuthenticator: UserAuthenticator,
    private val onLoginSuccess: suspend () -> Unit = {},
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    fun login(rawPassword: String) {
        if (this.mutableUiState.value is LoginUiState.Loading) return
        this.mutableUiState.value = LoginUiState.Loading
        this.viewModelScope.launch {
            val outcome = userAuthenticator.authenticate(rawPassword)
            when (outcome) {
                is Outcome.Success -> {
                    onLoginSuccess()
                    navigationChannel.send(NavigationTarget.Home)
                }
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error)
            }
        }
    }

    private fun mapError(error: DomainError): LoginUiState = when (error) {
        is LoginError.EmptyPassword -> LoginUiState.ValidationError(passwordError = error.externalMessage)
        is LoginError.InvalidCredentials -> LoginUiState.Error(error.externalMessage)
        is LoginError.CryptoFailure -> LoginUiState.Error(error.externalMessage)
        is LoginError.UserNotFound -> LoginUiState.Error(error.externalMessage)
        else -> LoginUiState.Error(error.externalMessage)
    }
}
