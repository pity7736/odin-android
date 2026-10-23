package dev.raiseexception.odin.accounting.presentation.incomeedit

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.IncomeUpdater
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.IncomeUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transaction
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
class EditIncomeViewModelTest {

    private val transactionFinder = mockk<TransactionFinder>()
    private val accountFinder = mockk<AccountFinder>()
    private val categoryLister = mockk<CategoryLister>()
    private val incomeUpdater = mockk<IncomeUpdater>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val incomeId = "inc-1"
    private val accountCreatedAt = Instant.parse("2026-03-01T12:00:00Z")
    private val account = AccountBuilder().id("acc-1").name("Ahorros").createdAt(accountCreatedAt).build()
    private val income = Income.restore(
        id = incomeId,
        accountId = "acc-1",
        amount = Money.of(BigDecimal("100000.50"), Currency.COP),
        date = LocalDate.parse("2026-03-05"),
        categoryId = "cat-salary",
        description = "Pago mensual",
        createdAt = Instant.parse("2026-03-05T12:00:00Z")
    )
    private val incomeCategories = listOf(
        CategoryBuilder().id("cat-salary").name("Salario").type(CategoryType.INCOME).build(),
        CategoryBuilder().id("cat-bonus").name("Bonos").type(CategoryType.INCOME).build()
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given an income, when loaded, then Editing is pre-filled with the income account and income categories`() =
        runTest {
            stubLoad()
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(EditIncomeUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as EditIncomeUiState.Editing
                assertEquals("100000.50", state.amount)
                assertEquals("2026-03-05", state.date)
                assertEquals("cat-salary", state.categoryId)
                assertEquals("Salario", state.categoryName)
                assertEquals("Pago mensual", state.description)
                assertEquals("Ahorros", state.accountName)
                assertEquals(
                    accountCreatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    state.accountCreatedAt
                )
                assertEquals(incomeCategories, state.categories)
                assertFalse(state.isSaving)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given the income does not exist, when loaded, then NotFound`() = runTest {
        every { transactionFinder.find(incomeId) } returns flowOf(
            Outcome.Failure(TransactionLookupError.NotFound("Not found", "Transacción no encontrada"))
        )
        assertLoadsNotFound()
    }

    @Test
    fun `given the id is an expense, when loaded, then NotFound`() = runTest {
        val expense = Expense.restore(
            id = incomeId,
            accountId = "acc-1",
            amount = Money.of(BigDecimal("1000"), Currency.COP),
            date = LocalDate.parse("2026-03-10"),
            categoryId = "cat-food",
            description = "",
            createdAt = Instant.parse("2026-03-10T12:00:00Z"),
            tagIds = emptyList(),
            gmfTax = null
        )
        every { transactionFinder.find(incomeId) } returns flowOf(Outcome.Success(detail(transaction = expense)))
        assertLoadsNotFound()
    }

    @Test
    fun `given a transfer income, when loaded, then NotFound`() = runTest {
        every { transactionFinder.find(incomeId) } returns flowOf(Outcome.Success(detail(isTransfer = true)))
        assertLoadsNotFound()
    }

    @Test
    fun `given the account or category lookup fails, when loaded, then NotFound`() = runTest {
        stubLoad()
        every { accountFinder.find("acc-1") } returns flowOf(Outcome.Failure(StorageError("account error")))
        assertLoadsNotFound()
        stubLoad()
        every { categoryLister.list(CategoryType.INCOME, "") } returns
            flowOf(Outcome.Failure(StorageError("category error")))
        assertLoadsNotFound()
    }

    @Test
    fun `given Editing, when saving successfully, then emits the navigation event`() = runTest {
        stubLoad()
        coEvery { incomeUpdater.update(any(), any(), any(), any(), any()) } returns Outcome.Success(income)
        val viewModel = loadedViewModel()
        viewModel.save("150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "Bono")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Unit, viewModel.navigationEvent.first())
        coVerify {
            incomeUpdater.update(incomeId, "150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "Bono")
        }
    }

    @Test
    fun `given Editing, when saving, then isSaving is true and previous errors are cleared`() = runTest {
        stubLoad()
        val pendingSave = CompletableDeferred<Outcome<Income>>()
        coEvery { incomeUpdater.update(any(), any(), any(), any(), any()) } returns
            invalidInputOutcome() coAndThen { pendingSave.await() }
        val viewModel = loadedViewModel()
        viewModel.save("0", "", CategoryInput.New(""), "")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "")
        val state = viewModel.uiState.value as EditIncomeUiState.Editing
        assertTrue(state.isSaving)
        assertNull(state.amountError)
        assertNull(state.dateError)
        assertNull(state.categoryError)
        assertNull(state.descriptionError)
        assertNull(state.saveError)
        pendingSave.complete(Outcome.Success(income))
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `given saving is in progress, when saving again, then the updater is called once`() = runTest {
        stubLoad()
        coEvery { incomeUpdater.update(any(), any(), any(), any(), any()) } returns Outcome.Success(income)
        val viewModel = loadedViewModel()
        viewModel.save("150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "")
        viewModel.save("150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { incomeUpdater.update(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given Loading, when saving, then the updater is not called`() = runTest {
        stubLoad()
        val viewModel = buildViewModel()
        viewModel.save("150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { incomeUpdater.update(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given InvalidInput, when saving, then Editing carries each field error and isSaving is false`() = runTest {
        stubLoad()
        coEvery { incomeUpdater.update(any(), any(), any(), any(), any()) } returns invalidInputOutcome()
        val viewModel = loadedViewModel()
        viewModel.save("0", "", CategoryInput.New(""), "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as EditIncomeUiState.Editing
        assertEquals("El saldo de la cuenta no puede quedar negativo.", state.amountError)
        assertEquals("La fecha es obligatoria.", state.dateError)
        assertEquals("El nombre es obligatorio.", state.categoryError)
        assertEquals("La descripción no puede superar los 500 caracteres.", state.descriptionError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `given StorageFailure, when saving, then Editing carries the spec save error`() = runTest {
        stubLoad()
        coEvery { incomeUpdater.update(any(), any(), any(), any(), any()) } returns
            Outcome.Failure(IncomeUpdateError.StorageFailure(internalMessage = "disk error"))
        val viewModel = loadedViewModel()
        viewModel.save("150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as EditIncomeUiState.Editing
        assertEquals("No se pudo editar el ingreso. Inténtalo de nuevo.", state.saveError)
        assertFalse(state.isSaving)
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given the save throws, when editing an income, then the prior state is restored and reported`() = runTest {
        stubLoad()
        val viewModel = loadedViewModel()
        val stateBeforeSaving = viewModel.uiState.value
        coEvery { incomeUpdater.update(any(), any(), any(), any(), any()) } throws IllegalStateException("falla")
        viewModel.save("150000", "2026-03-06", CategoryInput.Existing("cat-bonus"), "")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(stateBeforeSaving is EditIncomeUiState.Editing)
        assertEquals(stateBeforeSaving, viewModel.uiState.value)
        viewModel.unexpectedFailureEvent.test {
            assertEquals(Unit, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given loading throws, when loading the income, then UnexpectedFailure is shown and recorded`() = runTest {
        stubLoad()
        every { transactionFinder.find(incomeId) } returns flow<Outcome<TransactionDetail>> { error("falla") }
        val viewModel = loadedViewModel()
        assertEquals(EditIncomeUiState.UnexpectedFailure, viewModel.uiState.value)
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given UnexpectedFailure, when loading the income to edit again succeeds, then Editing is shown`() = runTest {
        stubLoad()
        every { transactionFinder.find(incomeId) } returns flow<Outcome<TransactionDetail>> { error("falla") }
        val viewModel = loadedViewModel()
        every { transactionFinder.find(incomeId) } returns flowOf(Outcome.Success(detail()))
        viewModel.retry()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is EditIncomeUiState.Editing)
    }

    private fun buildViewModel() = EditIncomeViewModel(
        incomeId = incomeId,
        transactionFinder = transactionFinder,
        accountFinder = accountFinder,
        categoryLister = categoryLister,
        incomeUpdater = incomeUpdater,
        failureRecorder = failureRecorder,
        ioDispatcher = testDispatcher
    )

    private fun stubLoad() {
        every { transactionFinder.find(incomeId) } returns flowOf(Outcome.Success(detail()))
        every { accountFinder.find("acc-1") } returns flowOf(Outcome.Success(account))
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(Outcome.Success(incomeCategories))
    }

    private fun detail(transaction: Transaction = this.income, isTransfer: Boolean = false): TransactionDetail =
        TransactionDetail(
            transaction,
            "Salario",
            "Ahorros",
            isTransfer = isTransfer,
            isGmfTax = false,
            accountType = AccountType.SAVINGS,
            tags = emptyList()
        )

    private fun loadedViewModel(): EditIncomeViewModel {
        val viewModel = this.buildViewModel()
        this.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private suspend fun assertLoadsNotFound() {
        val viewModel = this.buildViewModel()
        viewModel.uiState.test {
            assertEquals(EditIncomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(EditIncomeUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun invalidInputOutcome(): Outcome<Income> = Outcome.Failure(
        IncomeUpdateError.InvalidInput(
            amountError = "El saldo de la cuenta no puede quedar negativo.",
            dateError = "La fecha es obligatoria.",
            categoryError = "El nombre es obligatorio.",
            descriptionError = "La descripción no puede superar los 500 caracteres."
        )
    )
}
