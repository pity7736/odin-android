package dev.raiseexception.odin.accounting.presentation.accountedit

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountUpdater
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class EditAccountViewModelTest {

    private val accountFinder = mockk<AccountFinder>()
    private val accountUpdater = mockk<AccountUpdater>()
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
        EditAccountViewModel(accountId, accountFinder, accountUpdater, testDispatcher)

    @Test
    fun `given an existing account with no movements, when loaded, then Editing is prefilled and not locked`() =
        runTest {
            val existing = AccountBuilder()
                .id(accountId)
                .name("Ahorros")
                .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
                .type(AccountType.SAVINGS)
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
                assertEquals(AccountType.SAVINGS, state.type)
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
        viewModel.save("Corriente", "2000.00", Currency.COP, AccountType.CASH, "")
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
        viewModel.save("Corriente", "2000.00", Currency.COP, AccountType.CASH, "")
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
            viewModel.save("Corriente", "2000.00", Currency.COP, AccountType.CASH, "")
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value as EditAccountUiState.Editing
            assertEquals("No se pudo guardar la cuenta. Inténtalo de nuevo.", state.saveError)
            assertEquals("Ahorros", state.name)
            assertFalse(state.isSaving)
        }

    @Test
    fun `given a save in progress, when save is called again, then it is ignored`() = runTest {
        val existing = AccountBuilder().id(accountId).name("Ahorros").build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(existing))
        coEvery { accountUpdater.update(any(), any(), any(), any(), any(), any()) } returns Outcome.Success(existing)
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.save("Corriente", "2000.00", Currency.COP, AccountType.CASH, "")
        viewModel.save("Corriente", "2000.00", Currency.COP, AccountType.CASH, "")
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { accountUpdater.update(any(), any(), any(), any(), any(), any()) }
    }
}
