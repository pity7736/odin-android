package dev.raiseexception.odin.accounting.presentation.incomecreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
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
class CreateIncomeViewModelTest {

    private val incomeCreator = mockk<IncomeCreator>()
    private val categoryLister = mockk<CategoryLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "acc-1"
    private val incomeCategory = CategoryBuilder().type(CategoryType.INCOME).build()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CreateIncomeViewModel(
        accountId = accountId,
        incomeCreator = incomeCreator,
        categoryLister = categoryLister,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `given account id, when initialized, then loads income categories and transitions to idle`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )

        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(CreateIncomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateIncomeUiState.Idle
            assertEquals(1, state.categories.size)
            assertEquals(incomeCategory.id, state.categories.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid input, when saving, then navigates back to account detail`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = LocalDate(2026, 8, 29),
                categoryInput = CategoryInput.Existing(incomeCategory.id),
                description = ""
            )
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Income.restore(
                id = "inc-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = incomeCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", LocalDate(2026, 8, 29), incomeCategory.id, "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.AccountDetail(accountId), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given zero amount, when saving, then shows amount error`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            IncomeCreationError.InvalidInput(
                amountError = "El monto debe ser mayor que cero.",
                dateError = null,
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("0", LocalDate(2026, 8, 29), incomeCategory.id, "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateIncomeUiState.ValidationError
            assertNotNull(state.amountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given future date, when saving, then shows date error`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            IncomeCreationError.InvalidInput(
                amountError = null,
                dateError = "La fecha debe ser hoy o en el pasado.",
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", LocalDate(2099, 1, 1), incomeCategory.id, "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateIncomeUiState.ValidationError
            assertNotNull(state.dateError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given missing required field, when saving, then shows field error`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            IncomeCreationError.InvalidInput(
                amountError = "El monto es obligatorio.",
                dateError = "La fecha es obligatoria.",
                categoryError = "La categoría es obligatoria."
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("", null, "", "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateIncomeUiState.ValidationError
            assertNotNull(state.amountError)
            assertNotNull(state.dateError)
            assertNotNull(state.categoryError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given already saving, when save called again, then ignores duplicate call`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Success(
            dev.raiseexception.odin.accounting.domain.model.Income.restore(
                id = "inc-1",
                accountId = accountId,
                amount = dev.raiseexception.odin.accounting.domain.model.Money.of(
                    java.math.BigDecimal("500.00"),
                    dev.raiseexception.odin.accounting.domain.model.Currency.COP
                ),
                date = LocalDate(2026, 8, 29),
                categoryId = incomeCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", LocalDate(2026, 8, 29), incomeCategory.id, "")
        viewModel.save("500.00", LocalDate(2026, 8, 29), incomeCategory.id, "")
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { incomeCreator.create(any(), any(), any(), any(), any()) }
    }
}
