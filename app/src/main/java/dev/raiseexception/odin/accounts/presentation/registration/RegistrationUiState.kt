package dev.raiseexception.odin.accounts.presentation.registration

sealed interface RegistrationUiState {
    data object Idle : RegistrationUiState
    data object Loading : RegistrationUiState
    data object Success : RegistrationUiState
    data class ValidationError(
        val passwordError: String? = null,
        val passwordConfirmationError: String? = null
    ) : RegistrationUiState
    data class Error(val message: String) : RegistrationUiState
}
