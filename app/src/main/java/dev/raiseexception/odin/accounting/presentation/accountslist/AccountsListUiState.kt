package dev.raiseexception.odin.accounting.presentation.accountslist

import dev.raiseexception.odin.accounting.domain.model.Account

sealed interface AccountsListUiState {
    data object Loading : AccountsListUiState
    data object Empty : AccountsListUiState
    data class Content(
        val moneyAccounts: List<Account>,
        val creditCards: List<CreditCardItem>
    ) : AccountsListUiState
    data class Error(val message: String) : AccountsListUiState
}
