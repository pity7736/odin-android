package dev.raiseexception.odin.accounts.presentation.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RegistrationViewModel(
    private val userRegistrar: UserRegistrar
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<RegistrationUiState>(RegistrationUiState.Idle)
    val uiState: StateFlow<RegistrationUiState> = this.mutableUiState.asStateFlow()

    fun register(rawPassword: String, rawPasswordConfirmation: String) {
        this.viewModelScope.launch {
            mutableUiState.value = RegistrationUiState.Loading
            mutableUiState.value = mapOutcome(userRegistrar.register(rawPassword, rawPasswordConfirmation))
        }
    }

    private fun mapOutcome(outcome: Outcome<User>): RegistrationUiState = when (outcome) {
        is Outcome.Success -> RegistrationUiState.Success
        is Outcome.Failure -> this.mapError(outcome.error)
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
