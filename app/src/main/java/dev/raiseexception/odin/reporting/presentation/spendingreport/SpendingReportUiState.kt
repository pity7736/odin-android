package dev.raiseexception.odin.reporting.presentation.spendingreport

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.reporting.application.usecase.ReportPeriod
import dev.raiseexception.odin.reporting.application.usecase.SpendingReport

sealed interface SpendingReportUiState {
    data object Loading : SpendingReportUiState
    data class Content(val report: SpendingReport) : SpendingReportUiState
    data class Empty(
        val period: ReportPeriod,
        val currencies: List<Currency>,
        val currency: Currency,
    ) : SpendingReportUiState
    data class Error(val message: String) : SpendingReportUiState
}
