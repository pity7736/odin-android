package dev.raiseexception.odin.reporting.presentation.spendingreport

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.reporting.application.usecase.ReportPeriod
import dev.raiseexception.odin.reporting.application.usecase.SpendingReport
import dev.raiseexception.odin.reporting.application.usecase.SpendingReporter
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.launchObservation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

class SpendingReportViewModel(
    private val spendingReporter: SpendingReporter,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val reportRequest = MutableStateFlow(ReportRequest(this.spendingReporter.startingPeriod(), null))

    private val mutableUiState = MutableStateFlow<SpendingReportUiState>(SpendingReportUiState.Loading)
    val uiState: StateFlow<SpendingReportUiState> = this.mutableUiState.asStateFlow()

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun onPeriodSelected(start: LocalDate, end: LocalDate) {
        val period = this.spendingReporter.validPeriod(start, end) ?: return
        this.reportRequest.update { it.copy(period = period) }
    }

    fun onCurrencySelected(currency: Currency) {
        this.reportRequest.update { it.copy(chosenCurrency = currency) }
    }

    fun retry() {
        this.mutableUiState.value = SpendingReportUiState.Loading
        this.restartableLoad.start()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun load(): Job {
        val reportFlow = this.reportRequest
            .flatMapLatest { request -> this.spendingReporter.report(request.period, request.chosenCurrency) }
            .map { outcome -> this.mapToUiState(outcome) }
            .flowOn(this.ioDispatcher)
        return this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = reportFlow,
            onEach = { state -> this.mutableUiState.value = state },
            onUnexpectedFailure = { this.mutableUiState.value = SpendingReportUiState.UnexpectedFailure }
        )
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

    private companion object {
        const val LOAD_OPERATION = "spending-report.load"
    }
}
