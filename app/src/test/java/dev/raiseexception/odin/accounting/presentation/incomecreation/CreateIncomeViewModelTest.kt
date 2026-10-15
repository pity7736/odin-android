package dev.raiseexception.odin.accounting.presentation.incomecreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import dev.raiseexception.odin.testutil.CategoryBuilder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
class CreateIncomeViewModelTest {

    private val incomeCreator = mockk<IncomeCreator>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val categoryLister = mockk<CategoryLister>()
    private val accountFinder = mockk<AccountFinder>()
    private val accountLister = mockk<AccountLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "acc-1"
    private val accountCreatedAt = Instant.parse("2026-01-01T12:00:00Z")
    private val incomeCategory = CategoryBuilder().type(CategoryType.INCOME).build()
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

    private fun buildViewModel() = CreateIncomeViewModel(
        accountId = accountId,
        incomeCreator = incomeCreator,
        categoryLister = categoryLister,
        accountFinder = accountFinder,
        accountLister = accountLister,
        failureRecorder = failureRecorder,
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
            val expectedDate = accountCreatedAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
            assertEquals(expectedDate, state.accountCreatedAt)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid input with existing category, when saving, then navigates back to account detail`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
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

        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.AccountDetail(accountId), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid input with new category name, when saving, then navigates back to account detail`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(emptyList())
        )
        coEvery {
            incomeCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
                categoryInput = CategoryInput.New("Freelance"),
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
                categoryId = "new-cat-id",
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.New("Freelance"), "")
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

        viewModel.save("0", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateIncomeUiState.ValidationError
            assertNotNull(state.amountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given income creation fails with CreditCardAccount, when saving, then shows the error message`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            IncomeCreationError.CreditCardAccount(
                internalMessage = "Account acc-1 is a credit card and cannot receive an income",
                externalMessage = "Una tarjeta de crédito no puede recibir ingresos."
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            assertEquals(CreateIncomeUiState.Error("Una tarjeta de crédito no puede recibir ingresos."), awaitItem())
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

        viewModel.save("500.00", "2099-01-01", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem() as CreateIncomeUiState.ValidationError
            assertNotNull(state.dateError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given date before account creation, when saving, then shows date error`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        coEvery {
            incomeCreator.create(any(), any(), any(), any(), any())
        } returns Outcome.Failure(
            IncomeCreationError.InvalidInput(
                amountError = null,
                dateError = "La fecha no puede ser anterior a la fecha de creación de la cuenta.",
                categoryError = null
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save("500.00", "2025-12-31", CategoryInput.Existing(incomeCategory.id), "")
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

        viewModel.save("", "", CategoryInput.New(""), "")
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
    fun `given no account id, when initialized, then loads accounts for picker`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateIncomeViewModel(
            accountId = null,
            incomeCreator = incomeCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            failureRecorder = failureRecorder,
            ioDispatcher = testDispatcher
        )
        viewModel.uiState.test {
            assertEquals(CreateIncomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateIncomeUiState.Idle
            assertEquals(1, state.accounts.size)
            assertEquals(accountId, state.accounts.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a savings account and a credit card when opening from home then only the savings account is offered`() =
        runTest {
            val visaCard = AccountBuilder()
                .id("card-1")
                .creditCard(
                    creditLimit = Money.of(BigDecimal("5000000.00"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("200000.00"), Currency.COP)
                )
                .build()
            every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
                Outcome.Success(listOf(incomeCategory))
            )
            every { accountLister.list() } returns flowOf(
                Outcome.Success(listOf(account, visaCard))
            )
            val viewModel = CreateIncomeViewModel(
                accountId = null,
                incomeCreator = incomeCreator,
                categoryLister = categoryLister,
                accountFinder = accountFinder,
                accountLister = accountLister,
                failureRecorder = failureRecorder,
                ioDispatcher = testDispatcher
            )
            viewModel.uiState.test {
                assertEquals(CreateIncomeUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as CreateIncomeUiState.Idle
                assertEquals(listOf(accountId), state.accounts.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given no account id, when account selected, then updates selected account`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateIncomeViewModel(
            accountId = null,
            incomeCreator = incomeCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            failureRecorder = failureRecorder,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAccountSelected(accountId)
        viewModel.uiState.test {
            val state = awaitItem() as CreateIncomeUiState.Idle
            assertEquals(accountId, state.selectedAccountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id and no account selected, when saving, then shows account required error`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        val viewModel = CreateIncomeViewModel(
            accountId = null,
            incomeCreator = incomeCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            failureRecorder = failureRecorder,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        viewModel.uiState.test {
            val state = awaitItem() as CreateIncomeUiState.ValidationError
            assertEquals("La cuenta es obligatoria.", state.accountError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no account id and account selected, when saving, then creates with selected account`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        every { accountLister.list() } returns flowOf(
            Outcome.Success(listOf(account))
        )
        coEvery {
            incomeCreator.create(
                accountId = accountId,
                amount = "500.00",
                date = "2026-08-29",
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
                date = kotlinx.datetime.LocalDate(2026, 8, 29),
                categoryId = incomeCategory.id,
                description = "",
                createdAt = kotlinx.datetime.Instant.parse("2026-08-29T10:00:00Z")
            )
        )
        val viewModel = CreateIncomeViewModel(
            accountId = null,
            incomeCreator = incomeCreator,
            categoryLister = categoryLister,
            accountFinder = accountFinder,
            accountLister = accountLister,
            failureRecorder = failureRecorder,
            ioDispatcher = testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAccountSelected(accountId)
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.Back, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account id provided, when initialized, then does not load accounts for picker`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(
            Outcome.Success(listOf(incomeCategory))
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CreateIncomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateIncomeUiState.Idle
            assertTrue(state.accounts.isEmpty())
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

        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()

        io.mockk.coVerify(exactly = 1) { incomeCreator.create(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given the save throws, when saving an income, then the prior state is restored and reported`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(Outcome.Success(listOf(incomeCategory)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val stateBeforeSaving = viewModel.uiState.value
        coEvery { incomeCreator.create(any(), any(), any(), any(), any()) } throws IllegalStateException("falla")
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(stateBeforeSaving is CreateIncomeUiState.Idle)
        assertEquals(stateBeforeSaving, viewModel.uiState.value)
        viewModel.unexpectedFailureEvent.test {
            assertEquals(Unit, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given a save that takes 60 seconds, when saving an income, then it completes with no failure`() = runTest {
        every { categoryLister.list(CategoryType.INCOME, "") } returns flowOf(Outcome.Success(listOf(incomeCategory)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { incomeCreator.create(any(), any(), any(), any(), any()) } coAnswers {
            delay(60.seconds)
            Outcome.Success(
                Income.restore(
                    id = "inc-1",
                    accountId = accountId,
                    amount = Money.of(BigDecimal("500.00"), Currency.COP),
                    date = LocalDate(2026, 8, 29),
                    categoryId = incomeCategory.id,
                    description = "",
                    createdAt = Instant.parse("2026-08-29T10:00:00Z")
                )
            )
        }
        viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.AccountDetail(accountId), viewModel.navigationEvent.first())
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given an expected failure, when saving an income, then it shows as before and is not recorded`() =
        runTest {
            every { categoryLister.list(CategoryType.INCOME, "") } returns
                flowOf(Outcome.Success(listOf(incomeCategory)))
            coEvery { incomeCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
                IncomeCreationError.CreditCardAccount(internalMessage = "card", externalMessage = "No permitido")
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("500.00", "2026-08-29", CategoryInput.Existing(incomeCategory.id), "")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateIncomeUiState.Error("No permitido"), viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }

    @Test
    fun `given loading throws, when loading the income form, then UnexpectedFailure is shown and recorded`() =
        runTest {
            every { categoryLister.list(CategoryType.INCOME, "") } returns
                flow<Outcome<List<Category>>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateIncomeUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given no result within 10 seconds, when loading the income form, then UnexpectedFailure is shown`() =
        runTest {
            every { categoryLister.list(CategoryType.INCOME, "") } returns
                flow<Outcome<List<Category>>> { awaitCancellation() }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceTimeBy(10.seconds)
            testDispatcher.scheduler.runCurrent()
            assertEquals(CreateIncomeUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given UnexpectedFailure, when loading the income form again succeeds, then the normal content is shown`() =
        runTest {
            every { categoryLister.list(CategoryType.INCOME, "") } returns
                flow<Outcome<List<Category>>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            every { categoryLister.list(CategoryType.INCOME, "") } returns
                flowOf(Outcome.Success(listOf(incomeCategory)))
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is CreateIncomeUiState.Idle)
        }

    @Test
    fun `given UnexpectedFailure, when loading the income form again fails, then it is shown and recorded twice`() =
        runTest {
            every { categoryLister.list(CategoryType.INCOME, "") } returns
                flow<Outcome<List<Category>>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateIncomeUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 2) { failureRecorder.record(any()) }
        }

    @Test
    fun `given an expected failure, when loading the income form, then it shows as before and is not recorded`() =
        runTest {
            every { categoryLister.list(CategoryType.INCOME, "") } returns
                flowOf(Outcome.Failure(StorageError("DB error")))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateIncomeUiState.Error("Error al acceder a los datos"), viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }
}
