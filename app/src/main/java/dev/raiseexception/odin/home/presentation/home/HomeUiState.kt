package dev.raiseexception.odin.home.presentation.home

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.home.application.usecase.RecentTransaction

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(
        val totalBalances: List<Money>,
        val accounts: List<Account>,
        val hasMoreAccounts: Boolean,
        val recentTransactions: List<RecentTransaction>,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
