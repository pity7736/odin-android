package dev.raiseexception.odin.home.presentation.home

import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.home.application.usecase.HomeAccountEntry
import dev.raiseexception.odin.home.application.usecase.RecentTransaction

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(
        val balanceTotals: List<Money>,
        val debtTotals: List<Money>,
        val accounts: List<HomeAccountEntry>,
        val hasMoreAccounts: Boolean,
        val recentTransactions: List<RecentTransaction>,
        val canTransfer: Boolean,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
