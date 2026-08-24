package dev.raiseexception.odin.accounts.presentation.login

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class ValidationError(val passwordError: String? = null) : LoginUiState
    data class Error(val message: String) : LoginUiState
}
