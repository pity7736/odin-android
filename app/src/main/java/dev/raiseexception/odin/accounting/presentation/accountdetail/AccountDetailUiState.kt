package dev.raiseexception.odin.accounting.presentation.accountdetail

import dev.raiseexception.odin.accounting.domain.model.Account

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data class Content(val account: Account) : AccountDetailUiState
    data object NotFound : AccountDetailUiState
    data class Error(val message: String) : AccountDetailUiState
}
