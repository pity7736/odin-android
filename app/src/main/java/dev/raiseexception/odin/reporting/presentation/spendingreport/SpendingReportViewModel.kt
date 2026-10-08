package dev.raiseexception.odin.reporting.presentation.spendingreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.reporting.application.usecase.ReportPeriod
import dev.raiseexception.odin.reporting.application.usecase.SpendingReport
import dev.raiseexception.odin.reporting.application.usecase.SpendingReporter
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class SpendingReportViewModel(
    private val spendingReporter: SpendingReporter,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val reportRequest = MutableStateFlow(ReportRequest(this.spendingReporter.startingPeriod(), null))

    private val mutableUiState = MutableStateFlow<SpendingReportUiState>(SpendingReportUiState.Loading)
    val uiState: StateFlow<SpendingReportUiState> = this.mutableUiState.asStateFlow()

    init {
        this.load()
    }

    fun onPeriodSelected(start: LocalDate, end: LocalDate) {
        val period = this.spendingReporter.validPeriod(start, end) ?: return
        this.reportRequest.update { it.copy(period = period) }
    }

    fun onCurrencySelected(currency: Currency) {
        this.reportRequest.update { it.copy(chosenCurrency = currency) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun load() {
        this.viewModelScope.launch(this.ioDispatcher) {
            reportRequest
                .flatMapLatest { request -> spendingReporter.report(request.period, request.chosenCurrency) }
                .collect { outcome -> mutableUiState.value = mapToUiState(outcome) }
        }
    }

    private fun mapToUiState(outcome: Outcome<SpendingReport>): SpendingReportUiState = when (outcome) {
        is Outcome.Failure -> SpendingReportUiState.Error("Error al cargar el reporte")
        is Outcome.Success -> if (outcome.value.categories.isEmpty()) {
            SpendingReportUiState.Empty(outcome.value.period, outcome.value.currencies, outcome.value.currency)
        } else {
            SpendingReportUiState.Content(outcome.value)
        }
    }

    private data class ReportRequest(val period: ReportPeriod, val chosenCurrency: Currency?)
}
