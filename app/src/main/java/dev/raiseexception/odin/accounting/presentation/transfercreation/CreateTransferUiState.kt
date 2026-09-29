package dev.raiseexception.odin.accounting.presentation.transfercreation

import dev.raiseexception.odin.accounting.domain.model.Account

sealed interface CreateTransferUiState {
    data object Loading : CreateTransferUiState
    data class Idle(
        val sourceAccounts: List<Account>,
        val destinationAccounts: List<Account>,
        val selectedSourceAccountId: String,
        val selectedDestinationAccountId: String,
        val saveLabel: String
    ) : CreateTransferUiState
    data class Saving(
        val sourceAccounts: List<Account>,
        val destinationAccounts: List<Account>,
        val selectedSourceAccountId: String,
        val selectedDestinationAccountId: String,
        val saveLabel: String
    ) : CreateTransferUiState

    @Suppress("LongParameterList")
    data class ValidationError(
        val sourceAccounts: List<Account>,
        val destinationAccounts: List<Account>,
        val selectedSourceAccountId: String,
        val selectedDestinationAccountId: String,
        val saveLabel: String,
        val amountError: String? = null,
        val dateError: String? = null,
        val sourceAccountError: String? = null,
        val destinationAccountError: String? = null
    ) : CreateTransferUiState
    data class Error(val message: String) : CreateTransferUiState
}
