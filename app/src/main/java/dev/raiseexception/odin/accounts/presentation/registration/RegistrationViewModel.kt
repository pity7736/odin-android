package dev.raiseexception.odin.accounts.presentation.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class RegistrationViewModel(
    private val userRegistrar: UserRegistrar
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Idle)
    val uiState: StateFlow<RegistrationUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    fun register(rawPassword: String, rawPasswordConfirmation: String) {
        if (this.mutableUiState.value is RegistrationUiState.Loading) return
        this.mutableUiState.value = RegistrationUiState.Loading
        this.viewModelScope.launch {
            val outcome = userRegistrar.register(rawPassword, rawPasswordConfirmation)
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(NavigationTarget.Home)
                is Outcome.Failure -> mutableUiState.value = mapError(outcome.error)
            }
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
}
