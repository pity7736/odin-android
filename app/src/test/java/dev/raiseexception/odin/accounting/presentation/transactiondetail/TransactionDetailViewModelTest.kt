package dev.raiseexception.odin.accounting.presentation.transactiondetail

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeGreen
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {

    private val transactionFinder = mockk<TransactionFinder>()
    private val testDispatcher = StandardTestDispatcher()
    private val transactionId = "test-tx-id"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        TransactionDetailViewModel(transactionId, transactionFinder, testDispatcher)

    @Test
    fun `given a loading state, when the view model initializes, then emits loading`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Success(incomeDetail())
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given an existing income, when observing, then emits content with income styling`() = runTest {
        val detail = incomeDetail()
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals("+$1.000,00", state.formattedAmount)
            assertEquals(IncomeGreen, state.amountColor)
            assertEquals("14 de septiembre de 2026", state.formattedDate)
            assertEquals("Salario", state.categoryName)
            assertEquals("Ahorros", state.accountName)
            assertEquals("Pago mensual", state.description)
            assertEquals(true, state.isIncome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given an existing expense, when observing, then emits content with expense styling`() = runTest {
        val detail = expenseDetail()
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals("-$500,00", state.formattedAmount)
            assertEquals(ExpenseRed, state.amountColor)
            assertEquals("15 de septiembre de 2026", state.formattedDate)
            assertEquals("Alimentación", state.categoryName)
            assertEquals("Efectivo", state.accountName)
            assertEquals("Mercado semanal", state.description)
            assertEquals(false, state.isIncome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a missing transaction, when observing, then emits not found`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Failure(
                TransactionLookupError.NotFound(
                    internalMessage = "Transaction with id $transactionId not found",
                    externalMessage = "Transacción no encontrada"
                )
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(TransactionDetailUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a storage error, when observing, then emits error with external message`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Failure(
                StorageError(
                    internalMessage = "Database error",
                    externalMessage = "Error al acceder a los datos"
                )
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Error
            assertEquals("Error al acceder a los datos", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a transaction with empty description, when observing, then emits content with empty string`() = runTest {
        val detail = TransactionDetail(
            transaction = Income.restore(
                id = transactionId,
                accountId = "acc-1",
                amount = Money.of(BigDecimal("1000.00"), Currency.COP),
                date = LocalDate.parse("2026-09-14"),
                categoryId = "cat-1",
                description = "",
                createdAt = Instant.parse("2026-09-14T10:00:00Z")
            ),
            categoryName = "Salario",
            accountName = "Ahorros"
        )
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals("", state.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun incomeDetail(): TransactionDetail =
        TransactionDetail(
            transaction = Income.restore(
                id = transactionId,
                accountId = "acc-1",
                amount = Money.of(BigDecimal("1000.00"), Currency.COP),
                date = LocalDate.parse("2026-09-14"),
                categoryId = "cat-1",
                description = "Pago mensual",
                createdAt = Instant.parse("2026-09-14T10:00:00Z")
            ),
            categoryName = "Salario",
            accountName = "Ahorros"
        )

    private fun expenseDetail(): TransactionDetail =
        TransactionDetail(
            transaction = Expense.restore(
                id = transactionId,
                accountId = "acc-2",
                amount = Money.of(BigDecimal("500.00"), Currency.COP),
                date = LocalDate.parse("2026-09-15"),
                categoryId = "cat-2",
                description = "Mercado semanal",
                createdAt = Instant.parse("2026-09-15T10:00:00Z")
            ),
            categoryName = "Alimentación",
            accountName = "Efectivo"
        )
}
