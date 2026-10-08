package dev.raiseexception.odin.reporting.presentation.spendingreport

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.reporting.application.usecase.CategorySpending
import dev.raiseexception.odin.reporting.application.usecase.DisplayedShare
import dev.raiseexception.odin.reporting.application.usecase.ReportPeriod
import dev.raiseexception.odin.reporting.application.usecase.SpendingReport
import dev.raiseexception.odin.reporting.application.usecase.SpendingReporter
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
class SpendingReportViewModelTest {

    private val spendingReporter = mockk<SpendingReporter>()
    private val testDispatcher = StandardTestDispatcher()
    private val october = ReportPeriod(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-07"))
    private val september = ReportPeriod(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"))
    private val marketSpending = CategorySpending(
        categoryId = "cat-market",
        name = "Mercado",
        color = "#F97316",
        total = Money.of(BigDecimal("1250000.00"), Currency.COP),
        share = BigDecimal.ONE,
        displayedShare = DisplayedShare.Whole(100),
    )
    private val octoberReport = SpendingReport(
        period = october,
        currencies = listOf(Currency.COP, Currency.USD),
        currency = Currency.COP,
        total = Money.of(BigDecimal("1250000.00"), Currency.COP),
        categories = listOf(marketSpending),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(this.testDispatcher)
        every { spendingReporter.startingPeriod() } returns this.october
        every { spendingReporter.report(any(), any()) } returns flowOf(Outcome.Success(this.octoberReport))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given a new report, when initialized, then starts Loading and requests the starting period`() = runTest {
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(SpendingReportUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        verify { spendingReporter.report(october, null) }
    }

    @Test
    fun `given a report with categories, when initialized, then emits Content with the report`() = runTest {
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(SpendingReportUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(SpendingReportUiState.Content(octoberReport), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a report with no categories, when initialized, then emits Empty with its period and currencies`() =
        runTest {
            val emptyReport = octoberReport.copy(
                currency = Currency.USD,
                total = Money.of(BigDecimal.ZERO, Currency.USD),
                categories = emptyList(),
            )
            every { spendingReporter.report(any(), any()) } returns flowOf(Outcome.Success(emptyReport))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(SpendingReportUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(
                    SpendingReportUiState.Empty(october, listOf(Currency.COP, Currency.USD), Currency.USD),
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given the report fails, when initialized, then emits Error with the load message`() = runTest {
        every { spendingReporter.report(any(), any()) } returns flowOf(Outcome.Failure(StorageError("disk error")))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(SpendingReportUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(SpendingReportUiState.Error("Error al cargar el reporte"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a loaded report, when USD is selected, then requests the same period in USD`() = runTest {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onCurrencySelected(Currency.USD)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { spendingReporter.report(october, Currency.USD) }
    }

    @Test
    fun `given USD chosen, when a valid period is selected, then requests the new period keeping USD`() = runTest {
        every { spendingReporter.validPeriod(september.start, september.end) } returns september
        val viewModel = buildViewModel()
        viewModel.onCurrencySelected(Currency.USD)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onPeriodSelected(september.start, september.end)
        testDispatcher.scheduler.advanceUntilIdle()
        verify { spendingReporter.report(september, Currency.USD) }
    }

    @Test
    fun `given a loaded report, when an invalid period is selected, then nothing changes`() = runTest {
        val start = LocalDate.parse("2026-09-10")
        val end = LocalDate.parse("2026-09-09")
        every { spendingReporter.validPeriod(start, end) } returns null
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onPeriodSelected(start, end)
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 1) { spendingReporter.report(any(), any()) }
        assertEquals(SpendingReportUiState.Content(octoberReport), viewModel.uiState.value)
    }

    @Test
    fun `given an earlier report chose USD and September, when a new one starts, then it starts fresh`() = runTest {
        every { spendingReporter.validPeriod(september.start, september.end) } returns september
        val earlierViewModel = buildViewModel()
        earlierViewModel.onCurrencySelected(Currency.USD)
        earlierViewModel.onPeriodSelected(september.start, september.end)
        testDispatcher.scheduler.advanceUntilIdle()
        buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        verify(exactly = 2) { spendingReporter.startingPeriod() }
        verify(exactly = 1) { spendingReporter.report(october, null) }
        verify(exactly = 1) { spendingReporter.report(september, Currency.USD) }
    }

    private fun buildViewModel() = SpendingReportViewModel(this.spendingReporter, this.testDispatcher)
}
