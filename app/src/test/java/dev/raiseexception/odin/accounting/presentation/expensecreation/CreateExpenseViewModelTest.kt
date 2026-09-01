package dev.raiseexception.odin.accounting.presentation.expensecreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
class CreateExpenseViewModelTest {

    private val expenseCreator = mockk<ExpenseCreator>()
    private val categoryLister = mockk<CategoryLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "acc-1"
    private val expenseCategory = CategoryBuilder().type(CategoryType.EXPENSE).build()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CreateExpenseViewModel(
        accountId = accountId,
        expenseCreator = expenseCreator,
        categoryLister = categoryLister,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `given account id, when initialized, then loads expense categories and transitions to idle`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(CreateExpenseUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertEquals(1, state.categories.size)
            assertEquals(expenseCategory.id, state.categories.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid input with existing category, when saving, then navigates back to account detail`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
                categoryInput = CategoryInput.Existing(expenseCategory.id),
                description = ""
            )
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Expense.restore(
                id = "exp-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = expenseCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.AccountDetail(accountId), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid input with new category name, when saving, then navigates back to account detail`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(emptyList())
        )
        coEvery {
            expenseCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
                categoryInput = CategoryInput.New("Transporte"),
                description = ""
            )
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Expense.restore(
                id = "exp-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = "new-cat-id",
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.New("Transporte"), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.AccountDetail(accountId), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given zero amount, when saving, then shows amount error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = "El monto debe ser mayor que cero.",
                dateError = null,
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("0", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.amountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given future date, when saving, then shows date error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = null,
                dateError = "La fecha debe ser hoy o en el pasado.",
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2099-01-01", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.dateError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given missing required field, when saving, then shows field error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = "El monto es obligatorio.",
                dateError = "La fecha es obligatoria.",
                categoryError = "La categoría es obligatoria."
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("", "", CategoryInput.New(""), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.amountError)
            assertNotNull(state.dateError)
            assertNotNull(state.categoryError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given already saving, when save called again, then ignores duplicate call`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Expense.restore(
                id = "exp-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = expenseCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { expenseCreator.create(any(), any(), any(), any(), any()) }
    }
}
