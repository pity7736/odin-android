package dev.raiseexception.odin.accounting.presentation.accountedit

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountUpdater
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EditAccountViewModelTest {

    private val accountFinder = mockk<AccountFinder>()
    private val accountUpdater = mockk<AccountUpdater>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "acc-1"
    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        EditAccountViewModel(accountId, accountFinder, accountUpdater, failureRecorder, testDispatcher)

    @Test
    fun `given an existing account with no movements, when loaded, then Editing is prefilled and not locked`() =
        runTest {
            val existing = AccountBuilder()
                .id(accountId)
                .name("Ahorros")
                .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
                .kind(MoneyAccountKind.SAVINGS)
                .description("Fondo")
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(EditAccountUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as EditAccountUiState.Editing
                assertEquals("Ahorros", state.name)
                assertEquals(Currency.COP, state.currency)
                assertEquals(MoneyAccountKind.SAVINGS, state.kind)
                assertEquals("Fondo", state.description)
                assertFalse(state.locked)
                assertNull(state.lockedBalanceDisplay)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given an existing account with movements, when loaded, then Editing is prefilled and locked`() = runTest {
        val existing = AccountBuilder()
            .id(accountId)
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-01-01")
            .build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(EditAccountUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as EditAccountUiState.Editing
            assertTrue(state.locked)
            assertEquals("$1.000,00", state.lockedBalanceDisplay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a money account, when loaded, then editing state carries its kind`() = runTest {
        val existing = AccountBuilder().id(accountId).name("Efectivo").kind(MoneyAccountKind.CASH).build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(EditAccountUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as EditAccountUiState.Editing
            assertEquals(MoneyAccountKind.CASH, state.kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a credit card, when loaded, then editing state has no kind and shows the credit limit`() = runTest {
        val card = AccountBuilder()
            .id(accountId)
            .name("Visa")
            .creditCard(
                creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
                initialDebt = Money.of(BigDecimal("500000.00"), Currency.COP)
            )
            .build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(card))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(EditAccountUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as EditAccountUiState.Editing
            assertNull(state.kind)
            assertEquals("3000000.00", state.initialBalance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a non-existent account, when loaded, then NotFound`() = runTest {
        every { accountFinder.find(accountId, criteria) } returns flowOf(
            Outcome.Failure(
                AccountLookupError.NotFound(
                    internalMessage = "Not found",
                    externalMessage = "Cuenta no encontrada"
                )
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(EditAccountUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(EditAccountUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given valid changes, when save, then navigation event is emitted`() = runTest {
        val existing = AccountBuilder().id(accountId).name("Ahorros").build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Success(existing)
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Unit, viewModel.navigationEvent.first())
    }

    @Test
    fun `given invalid input, when save, then Editing shows the field errors and no navigation`() = runTest {
        val existing = AccountBuilder().id(accountId).name("Ahorros").build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Failure(
            AccountUpdateError.InvalidInput(
                nameError = "El nombre es obligatorio.",
                balanceError = "El saldo inicial es obligatorio.",
                currencyError = "La moneda es obligatoria.",
                typeError = "El tipo de cuenta es obligatorio.",
                descriptionError = "La descripción no puede superar los 500 caracteres."
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("", "", null, null, "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as EditAccountUiState.Editing
        assertEquals("El nombre es obligatorio.", state.nameError)
        assertEquals("El saldo inicial es obligatorio.", state.balanceError)
        assertEquals("La moneda es obligatoria.", state.currencyError)
        assertEquals("El tipo de cuenta es obligatorio.", state.typeError)
        assertEquals("La descripción no puede superar los 500 caracteres.", state.descriptionError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `given a duplicate name, when save, then Editing shows the name error`() = runTest {
        val existing = AccountBuilder().id(accountId).name("Ahorros").build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Failure(
            AccountUpdateError.DuplicateName(
                internalMessage = "duplicate",
                externalMessage = "Ya tienes una cuenta con ese nombre."
            )
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as EditAccountUiState.Editing
        assertEquals("Ya tienes una cuenta con ese nombre.", state.nameError)
    }

    @Test
    fun `given the save fails technically, when save, then Editing shows the save error and keeps the values`() =
        runTest {
            val existing = AccountBuilder().id(accountId).name("Ahorros").build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
            coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Failure(
                AccountUpdateError.StorageFailure(
                    internalMessage = "storage",
                    externalMessage = "No se pudo guardar la cuenta. Inténtalo de nuevo."
                )
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value as EditAccountUiState.Editing
            assertEquals("No se pudo guardar la cuenta. Inténtalo de nuevo.", state.saveError)
            assertEquals("Ahorros", state.name)
            assertFalse(state.isSaving)
        }

    @Test
    fun `given a credit card, when save, then saveError is the card not editable message`() = runTest {
        val card = AccountBuilder()
            .id(accountId)
            .name("Visa")
            .creditCard(Money.of(BigDecimal("3000000.00"), Currency.COP), Money.of(BigDecimal("0"), Currency.COP))
            .build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(card))
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Failure(
            AccountUpdateError.CreditCardNotEditable()
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("Visa", "3000000.00", Currency.COP, null, "")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as EditAccountUiState.Editing
        assertEquals("Las tarjetas de crédito no se pueden editar.", state.saveError)
        assertFalse(state.isSaving)
    }

    @Test
    fun `given a save in progress, when save is called again, then it is ignored`() = runTest {
        val existing = AccountBuilder().id(accountId).name("Ahorros").build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Success(existing)
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
        viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { accountUpdater.update(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given the save throws, when editing an account, then the prior state is restored and reported`() = runTest {
        every { accountFinder.find(accountId, criteria) } returns flowOf(
            Outcome.Success(AccountBuilder().id(accountId).name("Ahorros").build())
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val stateBeforeSaving = viewModel.uiState.value
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("falla")
        viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(stateBeforeSaving is EditAccountUiState.Editing)
        assertEquals(stateBeforeSaving, viewModel.uiState.value)
        viewModel.unexpectedFailureEvent.test {
            assertEquals(Unit, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given a save that takes 60 seconds, when editing an account, then it completes with no failure`() = runTest {
        val existing = AccountBuilder().id(accountId).name("Ahorros").build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } coAnswers {
            delay(60.seconds)
            Outcome.Success(existing)
        }
        viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(Unit, viewModel.navigationEvent.first())
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given an expected failure, when editing an account, then it shows as before and is not recorded`() =
        runTest {
            every { accountFinder.find(accountId, criteria) } returns flowOf(
                Outcome.Success(AccountBuilder().id(accountId).name("Ahorros").build())
            )
            coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Failure(
                AccountUpdateError.StorageFailure("storage", "No se pudo guardar")
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("Corriente", "2000.00", Currency.COP, MoneyAccountKind.CASH, "")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("No se pudo guardar", (viewModel.uiState.value as EditAccountUiState.Editing).saveError)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }

    @Test
    fun `given loading throws, when loading the account, then UnexpectedFailure is shown and recorded`() =
        runTest {
            every { accountFinder.find(accountId, criteria) } returns flow<Outcome<Account>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(EditAccountUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given no result within 10 seconds, when loading the account, then UnexpectedFailure is shown`() =
        runTest {
            every { accountFinder.find(accountId, criteria) } returns flow<Outcome<Account>> { awaitCancellation() }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceTimeBy(10.seconds)
            testDispatcher.scheduler.runCurrent()
            assertEquals(EditAccountUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given UnexpectedFailure, when loading the account to edit again succeeds, then the normal content is shown`() =
        runTest {
            every { accountFinder.find(accountId, criteria) } returns flow<Outcome<Account>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            every {
                accountFinder.find(accountId, criteria)
            } returns flowOf(Outcome.Success(AccountBuilder().id(accountId).name("Ahorros").build()))
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is EditAccountUiState.Editing)
        }

    @Test
    fun `given UnexpectedFailure, when loading the account to edit again fails, then it is shown and recorded twice`() =
        runTest {
            every { accountFinder.find(accountId, criteria) } returns flow<Outcome<Account>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(EditAccountUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 2) { failureRecorder.record(any()) }
        }

    @Test
    fun `given an expected failure, when loading the account, then it shows as before and is not recorded`() =
        runTest {
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Failure(StorageError("DB error")))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(EditAccountUiState.NotFound, viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }
}
