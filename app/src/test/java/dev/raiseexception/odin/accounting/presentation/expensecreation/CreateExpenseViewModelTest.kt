package dev.raiseexception.odin.accounting.presentation.expensecreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
class CreateExpenseViewModelTest {

    private val expenseCreator = mockk<ExpenseCreator>()
    private val categoryLister = mockk<CategoryLister>()
    private val accountFinder = mockk<AccountFinder>()
    private val accountLister = mockk<AccountLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "acc-1"
    private val accountCreatedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val expenseCategory = CategoryBuilder().type(CategoryType.EXPENSE).build()
    private val account = AccountBuilder().id(accountId).createdAt(accountCreatedAt).build()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { accountFinder.find(accountId) } returns flowOf(Outcome.Success(account))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CreateExpenseViewModel(
        accountId = accountId,
        expenseCreator = expenseCreator,
        categoryLister = categoryLister,
        accountFinder = accountFinder,
        accountLister = accountLister,
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
            val expectedDate = accountCreatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
            assertEquals(expectedDate, state.accountCreatedAt)
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
    fun `given date before account creation, when saving, then shows date error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = null,
                dateError = "La fecha no puede ser anterior a la fecha de creación de la cuenta.",
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2025-12-31", CategoryInput.Existing(expenseCategory.id), "")
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
    fun `given amount exceeds balance, when saving, then shows amount error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        coEvery {
            expenseCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            ExpenseCreationError.InvalidInput(
                amountError = "El monto supera el saldo disponible.",
                dateError = null,
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("999999.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertNotNull(state.amountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id, when initialized, then loads accounts for picker`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            ioDispatcher = testDispatcher
        )
        viewModel.uiState.test {
            assertEquals(CreateExpenseUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertEquals(1, state.accounts.size)
            assertEquals(accountId, state.accounts.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id, when account selected, then updates selected account`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAccountSelected(accountId)
        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertEquals(accountId, state.selectedAccountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id and no account selected, when saving, then shows account required error`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        viewModel.uiState.test {
            val state = awaitItem() as CreateExpenseUiState.ValidationError
            assertEquals("La cuenta es obligatoria.", state.accountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id and account selected, when saving, then creates with selected account`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
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
                date = kotlinx.datetime.LocalDate(2026, 8, 29),
                categoryId = expenseCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = CreateExpenseViewModel(
            accountId = null,
            expenseCreator = expenseCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAccountSelected(accountId)
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(expenseCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.Back, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account id provided, when initialized, then does not load accounts for picker`() = runTest {
        every { categoryLister.list(CategoryType.EXPENSE, "") } returns flowOf(
            Outcome.Success(listOf(expenseCategory))
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CreateExpenseUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateExpenseUiState.Idle
            assertTrue(state.accounts.isEmpty())
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
