package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Money

data class HomeSummary(
    val balanceTotals: List<Money>,
    val debtTotals: List<Money>,
    val entries: List<HomeAccountEntry>,
    val hasMoreEntries: Boolean,
    val recentTransactions: List<RecentTransaction>,
    val canTransfer: Boolean,
    val canRecordIncome: Boolean,
)
