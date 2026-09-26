package dev.raiseexception.odin.accounting.presentation.expenseedit

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseUpdater
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.ExpenseUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class EditExpenseViewModelTest {

    private val transactionFinder = mockk<TransactionFinder>()
    private val accountFinder = mockk<AccountFinder>()
    private val categoryLister = mockk<CategoryLister>()
    private val expenseUpdater = mockk<ExpenseUpdater>()
    private val testDispatcher = StandardTestDispatcher()
    private val expenseId = "exp-1"
    private val accountCreatedAt = Instant.parse("2026-03-01T12:00:00Z")
    private val account = AccountBuilder().id("acc-1").name("Ahorros").createdAt(accountCreatedAt).build()
    private val expense = Expense.restore(
        id = expenseId,
        accountId = "acc-1",
        amount = Money.of(BigDecimal("30000.50"), Currency.COP),
        date = LocalDate.parse("2026-03-10"),
        categoryId = "cat-food",
        description = "Mercado",
        createdAt = Instant.parse("2026-03-10T12:00:00Z")
    )
    private val expenseCategories = listOf(
        CategoryBuilder().id("cat-food").name("Alimentación").type(CategoryType.EXPENSE).build(),
        CategoryBuilder().id("cat-transport").name("Transporte").type(CategoryType.EXPENSE).build()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = EditExpenseViewModel(
        expenseId = expenseId,
        transactionFinder = transactionFinder,
        accountFinder = accountFinder,
        categoryLister = categoryLister,
        expenseUpdater = expenseUpdater,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `given an expense, when loaded, then Editing is pre-filled with the expense account and categories`() =
        runTest {
            stubLoad()
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(EditExpenseUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as EditExpenseUiState.Editing
                assertEquals("30000.50", state.amount)
                assertEquals("2026-03-10", state.date)
                assertEquals("cat-food", state.categoryId)
                assertEquals("Alimentación", state.categoryName)
                assertEquals("Mercado", state.description)
                assertEquals("Ahorros", state.accountName)
                assertEquals(
                    accountCreatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    state.accountCreatedAt
                )
                assertEquals(expenseCategories, state.categories)
                assertFalse(state.isSaving)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given the expense does not exist, when loaded, then NotFound`() = runTest {
        every { transactionFinder.find(expenseId) } returns flowOf(
            Outcome.Failure(TransactionLookupError.NotFound("Not found", "Transacción no encontrada"))
        )
        assertLoadsNotFound()
    }

    @Test
    fun `given the id is an income, when loaded, then NotFound`() = runTest {
        val income = Income.restore(
            id = expenseId,
            accountId = "acc-1",
            amount = Money.of(BigDecimal("1000"), Currency.COP),
            date = LocalDate.parse("2026-03-10"),
            categoryId = "cat-salary",
            description = "",
            createdAt = Instant.parse("2026-03-10T12:00:00Z")
        )
        every { transactionFinder.find(expenseId) } returns flowOf(
            Outcome.Success(TransactionDetail(income, "Salario", "Ahorros", isTransfer = false))
        )
        assertLoadsNotFound()
    }

    @Test
    fun `given a transfer expense, when loaded, then NotFound`() = runTest {
        every { transactionFinder.find(expenseId) } returns flowOf(
            Outcome.Success(TransactionDetail(expense, "Transferencia", "Ahorros", isTransfer = true))
        )
        assertLoadsNotFound()
    }

    @Test
    fun `given the account or category lookup fails, when loaded, then NotFound`() = runTest {
        stubLoad()
        every { accountFinder.find("acc-1") } returns flowOf(Outcome.Failure(StorageError("account error")))
        assertLoadsNotFound()
        stubLoad()
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns
            flowOf(Outcome.Failure(StorageError("category error")))
        assertLoadsNotFound()
    }

    @Test
    fun `given Editing, when saving successfully, then emits the navigation event`() = runTest {
        stubLoad()
        coEvery { expenseUpdater.update(any(), any(), any(), any(), any()) } returns Outcome.Success(expense)
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("45000", "2026-04-02", CategoryInput.Existing("cat-transport"), "Bus")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Unit, viewModel.navigationEvent.first())
        coVerify {
            expenseUpdater.update(expenseId, "45000", "2026-04-02", CategoryInput.Existing("cat-transport"), "Bus")
        }
    }

    @Test
    fun `given Editing, when saving, then isSaving is true and previous errors are cleared`() = runTest {
        stubLoad()
        val pendingSave = CompletableDeferred<Outcome<Expense>>()
        coEvery { expenseUpdater.update(any(), any(), any(), any(), any()) } returns
            invalidInputOutcome() coAndThen { pendingSave.await() }
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("0", "", CategoryInput.New(""), "")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("45000", "2026-04-02", CategoryInput.Existing("cat-food"), "")
        val state = viewModel.uiState.value as EditExpenseUiState.Editing
        assertTrue(state.isSaving)
        assertNull(state.amountError)
        assertNull(state.dateError)
        assertNull(state.categoryError)
        assertNull(state.descriptionError)
        assertNull(state.saveError)
        pendingSave.complete(Outcome.Success(expense))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `given saving is in progress, when saving again, then the updater is called once`() = runTest {
        stubLoad()
        coEvery { expenseUpdater.update(any(), any(), any(), any(), any()) } returns Outcome.Success(expense)
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("45000", "2026-04-02", CategoryInput.Existing("cat-food"), "")
        viewModel.save("45000", "2026-04-02", CategoryInput.Existing("cat-food"), "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { expenseUpdater.update(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given InvalidInput, when saving, then Editing carries each field error and isSaving is false`() = runTest {
        stubLoad()
        coEvery { expenseUpdater.update(any(), any(), any(), any(), any()) } returns invalidInputOutcome()
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("0", "", CategoryInput.New(""), "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as EditExpenseUiState.Editing
        assertEquals("El monto debe ser mayor que cero.", state.amountError)
        assertEquals("La fecha es obligatoria.", state.dateError)
        assertEquals("El nombre es obligatorio.", state.categoryError)
        assertEquals("La descripción no puede superar los 500 caracteres.", state.descriptionError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `given StorageFailure, when saving, then Editing carries the spec save error`() = runTest {
        stubLoad()
        coEvery { expenseUpdater.update(any(), any(), any(), any(), any()) } returns
            Outcome.Failure(ExpenseUpdateError.StorageFailure(internalMessage = "disk error"))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("45000", "2026-04-02", CategoryInput.Existing("cat-food"), "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as EditExpenseUiState.Editing
        assertEquals("No se pudo editar el gasto. Inténtalo de nuevo.", state.saveError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `given NotFound or TransferNotEditable on save, when saving, then saveError carries its external message`() =
        runTest {
            stubLoad()
            coEvery { expenseUpdater.update(any(), any(), any(), any(), any()) } returns Outcome.Failure(
                TransactionLookupError.NotFound("gone", "Transacción no encontrada")
            ) andThen Outcome.Failure(ExpenseUpdateError.TransferNotEditable(internalMessage = "transfer"))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("45000", "2026-04-02", CategoryInput.Existing("cat-food"), "")
            testDispatcher.scheduler.advanceUntilIdle()
            val notFoundState = viewModel.uiState.value as EditExpenseUiState.Editing
            viewModel.save("45000", "2026-04-02", CategoryInput.Existing("cat-food"), "")
            testDispatcher.scheduler.advanceUntilIdle()
            val transferState = viewModel.uiState.value as EditExpenseUiState.Editing
            assertEquals("Transacción no encontrada", notFoundState.saveError)
            assertEquals("Las transferencias no se pueden editar.", transferState.saveError)
            assertFalse(transferState.isSaving)
        }

    private fun stubLoad() {
        every { transactionFinder.find(expenseId) } returns flowOf(
            Outcome.Success(TransactionDetail(expense, "Alimentación", "Ahorros", isTransfer = false))
        )
        every { accountFinder.find("acc-1") } returns flowOf(Outcome.Success(account))
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(Outcome.Success(expenseCategories))
    }

    private suspend fun assertLoadsNotFound() {
        val viewModel = this.buildViewModel()
        viewModel.uiState.test {
            assertEquals(EditExpenseUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(EditExpenseUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun invalidInputOutcome(): Outcome<Expense> = Outcome.Failure(
        ExpenseUpdateError.InvalidInput(
            amountError = "El monto debe ser mayor que cero.",
            dateError = "La fecha es obligatoria.",
            categoryError = "El nombre es obligatorio.",
            descriptionError = "La descripción no puede superar los 500 caracteres."
        )
    )
}
