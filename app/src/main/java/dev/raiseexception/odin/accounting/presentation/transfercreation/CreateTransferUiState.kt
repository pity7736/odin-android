package dev.raiseexception.odin.accounting.presentation.transfercreation

import dev.raiseexception.odin.accounting.domain.model.Account

sealed interface CreateTransferUiState {
    data object Loading : CreateTransferUiState
    data class Idle(
        val accounts: List<Account>,
        val selectedSourceAccountId: String
    ) : CreateTransferUiState
    data object Saving : CreateTransferUiState
    data class ValidationError(
        val accounts: List<Account>,
        val amountError: String? = null,
        val dateError: String? = null,
        val sourceAccountError: String? = null,
        val destinationAccountError: String? = null
    ) : CreateTransferUiState
    data class Error(val message: String) : CreateTransferUiState
}
