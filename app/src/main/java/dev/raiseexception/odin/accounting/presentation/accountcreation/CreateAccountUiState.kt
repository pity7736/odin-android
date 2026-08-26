package dev.raiseexception.odin.accounting.presentation.accountcreation

sealed interface CreateAccountUiState {
    data object Idle : CreateAccountUiState
    data object Loading : CreateAccountUiState
    data class ValidationError(
        val nameError: String? = null,
        val balanceError: String? = null,
        val currencyError: String? = null,
        val typeError: String? = null,
        val descriptionError: String? = null
    ) : CreateAccountUiState
    data class Error(val message: String) : CreateAccountUiState
}
