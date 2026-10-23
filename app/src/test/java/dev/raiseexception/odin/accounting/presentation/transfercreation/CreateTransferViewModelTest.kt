package dev.raiseexception.odin.accounting.presentation.transfercreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.Transfer
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTransferViewModelTest {

    private val transferCreator = mockk<TransferCreator>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val accountLister = mockk<AccountLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val transferDate = LocalDate.parse("2026-08-29")
    private val savingsAccount = AccountBuilder()
        .id("src-1")
        .name("Ahorros")
        .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
        .build()
    private val cashAccount = AccountBuilder()
        .id("dst-1")
        .name("Efectivo")
        .kind(MoneyAccountKind.CASH)
        .initialBalance(Money.of(BigDecimal("500.00"), Currency.COP))
        .build()
    private val creditCard = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .creditCard(
            creditLimit = Money.of(BigDecimal("3000.00"), Currency.COP),
            initialDebt = Money.of(BigDecimal("500.00"), Currency.COP)
        )
        .build()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given savings cash and a card with no origin, when accounts load, then only money accounts are sources`() =
        runTest {
            val viewModel = loadedViewModel(originAccountId = null)

            val state = viewModel.uiState.value as CreateTransferUiState.Idle
            assertEquals(listOf(savingsAccount, cashAccount), state.sourceAccounts)
            assertEquals(listOf(savingsAccount, cashAccount, creditCard), state.destinationAccounts)
            assertEquals("", state.selectedSourceAccountId)
            assertEquals("", state.selectedDestinationAccountId)
            assertEquals("Transferir", state.saveLabel)
        }

    @Test
    fun `given a money account as origin, when accounts load, then it is the source and not a destination`() =
        runTest {
            val viewModel = loadedViewModel(originAccountId = "src-1")

            val state = viewModel.uiState.value as CreateTransferUiState.Idle
            assertEquals("src-1", state.selectedSourceAccountId)
            assertEquals("", state.selectedDestinationAccountId)
            assertEquals(listOf(cashAccount, creditCard), state.destinationAccounts)
        }

    @Test
    fun `given a card as origin, when accounts load, then it is the destination and the label reads Pagar`() =
        runTest {
            val viewModel = loadedViewModel(originAccountId = "card-1")

            val state = viewModel.uiState.value as CreateTransferUiState.Idle
            assertEquals("", state.selectedSourceAccountId)
            assertEquals("card-1", state.selectedDestinationAccountId)
            assertEquals("Pagar", state.saveLabel)
        }

    @Test
    fun `given the loading state, when accounts load, then emits Idle after Loading`() = runTest {
        every { accountLister.list() } returns flowOf(Outcome.Success(allAccounts()))
        val viewModel = buildViewModel(originAccountId = "src-1")
        viewModel.uiState.test {
            assertEquals(CreateTransferUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("src-1", (awaitItem() as CreateTransferUiState.Idle).selectedSourceAccountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given accounts load fails, when init, then emits Error`() = runTest {
        every { accountLister.list() } returns flowOf(Outcome.Failure(StorageError("DB error")))
        val viewModel = buildViewModel(originAccountId = "src-1")
        viewModel.uiState.test {
            assertEquals(CreateTransferUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertNotNull((awaitItem() as CreateTransferUiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given the cash account as destination, when selecting it as source, then it is excluded and cleared`() =
        runTest {
            val viewModel = loadedViewModel(originAccountId = "src-1")
            viewModel.onDestinationSelected("dst-1")

            viewModel.onSourceSelected("dst-1")

            val state = viewModel.uiState.value as CreateTransferUiState.Idle
            assertEquals("dst-1", state.selectedSourceAccountId)
            assertEquals("", state.selectedDestinationAccountId)
            assertEquals(listOf(savingsAccount, creditCard), state.destinationAccounts)
        }

    @Test
    fun `given a card as destination, when selecting another source, then the destination is kept`() = runTest {
        val viewModel = loadedViewModel(originAccountId = "card-1")

        viewModel.onSourceSelected("src-1")

        val state = viewModel.uiState.value as CreateTransferUiState.Idle
        assertEquals("src-1", state.selectedSourceAccountId)
        assertEquals("card-1", state.selectedDestinationAccountId)
        assertEquals(listOf(cashAccount, creditCard), state.destinationAccounts)
    }

    @Test
    fun `given a transfer, when choosing a card then a money account, then label goes Pagar to Transferir`() =
        runTest {
            val viewModel = loadedViewModel(originAccountId = "src-1")

            viewModel.onDestinationSelected("card-1")
            val cardLabel = (viewModel.uiState.value as CreateTransferUiState.Idle).saveLabel
            viewModel.onDestinationSelected("dst-1")
            val moneyAccountLabel = (viewModel.uiState.value as CreateTransferUiState.Idle).saveLabel

            assertEquals("Pagar", cardLabel)
            assertEquals("Transferir", moneyAccountLabel)
        }

    @Test
    fun `given a validation error, when selecting a source, then keeps errors and clears the source error`() =
        runTest {
            val viewModel = viewModelWithAccountErrors()

            viewModel.onSourceSelected("dst-1")

            val state = viewModel.uiState.value as CreateTransferUiState.ValidationError
            assertEquals("dst-1", state.selectedSourceAccountId)
            assertEquals(listOf(savingsAccount, creditCard), state.destinationAccounts)
            assertNull(state.sourceAccountError)
            assertEquals("Selecciona una cuenta destino.", state.destinationAccountError)
            assertEquals("El monto debe ser mayor que cero.", state.amountError)
        }

    @Test
    fun `given a validation error, when selecting a card as destination, then clears the destination error`() =
        runTest {
            val viewModel = viewModelWithAccountErrors()

            viewModel.onDestinationSelected("card-1")

            val state = viewModel.uiState.value as CreateTransferUiState.ValidationError
            assertEquals("card-1", state.selectedDestinationAccountId)
            assertEquals("Pagar", state.saveLabel)
            assertNull(state.destinationAccountError)
            assertEquals("Selecciona una cuenta origen.", state.sourceAccountError)
        }

    @Test
    fun `given selections made in the form, when saving, then creates the transfer with the selected accounts`() =
        runTest {
            coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(transfer())
            val viewModel = loadedViewModel(originAccountId = "card-1")
            viewModel.onSourceSelected("src-1")

            viewModel.onAmountChanged("200,00")

            viewModel.save("2026-08-29")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify {
                transferCreator.create(
                    sourceAccountId = "src-1",
                    destinationAccountId = "card-1",
                    amount = "200.00",
                    date = "2026-08-29",
                    chargeGmf = false
                )
            }
        }

    @Test
    fun `given a payment being filled, when the save fails validation, then keeps selections lists and label`() =
        runTest {
            coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
                TransferCreationError.InvalidInput(
                    amountError = "El pago no puede superar la deuda actual.",
                    dateError = null,
                    sourceAccountError = null,
                    destinationAccountError = null
                )
            )
            val viewModel = loadedViewModel(originAccountId = "src-1")
            viewModel.onDestinationSelected("card-1")

            viewModel.onAmountChanged("600,00")

            viewModel.save("2026-08-29")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                CreateTransferUiState.ValidationError(
                    sourceAccounts = listOf(savingsAccount, cashAccount),
                    destinationAccounts = listOf(cashAccount, creditCard),
                    selectedSourceAccountId = "src-1",
                    selectedDestinationAccountId = "card-1",
                    saveLabel = "Pagar",
                    amount = "600,00",
                    amountError = "El pago no puede superar la deuda actual."
                ),
                viewModel.uiState.value
            )
        }

    @Test
    fun `given a payment being filled, when saving starts, then the saving state keeps selections lists and label`() =
        runTest {
            coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(transfer())
            val viewModel = loadedViewModel(originAccountId = "src-1")
            viewModel.onDestinationSelected("card-1")

            viewModel.onAmountChanged("200,00")

            viewModel.save("2026-08-29")

            assertEquals(
                CreateTransferUiState.Saving(
                    sourceAccounts = listOf(savingsAccount, cashAccount),
                    destinationAccounts = listOf(cashAccount, creditCard),
                    selectedSourceAccountId = "src-1",
                    selectedDestinationAccountId = "card-1",
                    saveLabel = "Pagar",
                    amount = "200,00"
                ),
                viewModel.uiState.value
            )
        }

    @Test
    fun `given a valid transfer, when saving, then navigates back to the source account`() = runTest {
        coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(transfer())
        val viewModel = loadedViewModel(originAccountId = "src-1")
        viewModel.onDestinationSelected("dst-1")

        viewModel.onAmountChanged("200,00")

        viewModel.save("2026-08-29")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            assertEquals(NavigationTarget.AccountDetail("src-1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given Idle state, when saving and the transfer fails to save, then emits Error with the external message`() =
        runTest {
            coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
                TransferCreationError.StorageFailure(
                    internalMessage = "Failed to add income",
                    externalMessage = "No se pudo guardar la transferencia."
                )
            )
            val viewModel = loadedViewModel(originAccountId = "src-1")

            viewModel.onAmountChanged("100")

            viewModel.save("2026-08-29")
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(CreateTransferUiState.Error("No se pudo guardar la transferencia."), viewModel.uiState.value)
        }

    @Test
    fun `given Saving state, when save called again, then ignores duplicate`() = runTest {
        coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(transfer())
        val viewModel = loadedViewModel(originAccountId = "src-1")
        viewModel.onDestinationSelected("dst-1")

        viewModel.onAmountChanged("200,00")

        viewModel.save("2026-08-29")
        viewModel.onAmountChanged("200,00")
        viewModel.save("2026-08-29")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transferCreator.create(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `given the saving state, when a selection event arrives, then the state is unchanged`() = runTest {
        coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Success(transfer())
        val viewModel = loadedViewModel(originAccountId = "src-1")
        viewModel.onAmountChanged("200,00")
        viewModel.save("2026-08-29")

        val savingState = viewModel.uiState.value

        viewModel.onSourceSelected("dst-1")
        viewModel.onDestinationSelected("card-1")

        assertEquals(savingState, viewModel.uiState.value)
    }

    private fun loadedViewModel(originAccountId: String?): CreateTransferViewModel {
        every { accountLister.list() } returns flowOf(Outcome.Success(this.allAccounts()))
        val viewModel = this.buildViewModel(originAccountId)
        this.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun viewModelWithAccountErrors(): CreateTransferViewModel {
        coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
            TransferCreationError.InvalidInput(
                amountError = "El monto debe ser mayor que cero.",
                dateError = null,
                sourceAccountError = "Selecciona una cuenta origen.",
                destinationAccountError = "Selecciona una cuenta destino."
            )
        )
        val viewModel = this.loadedViewModel(originAccountId = null)
        viewModel.onAmountChanged("0")
        viewModel.save("2026-08-29")
        this.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun allAccounts(): List<Account> = listOf(this.savingsAccount, this.cashAccount, this.creditCard)

    private fun buildViewModel(originAccountId: String?) = CreateTransferViewModel(
        originAccountId = originAccountId,
        transferCreator = this.transferCreator,
        accountLister = this.accountLister,
        failureRecorder = this.failureRecorder,
        ioDispatcher = this.testDispatcher
    )

    private fun transfer(): Transfer {
        val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
        return Transfer.restore(
            id = "transfer-1",
            expense = Expense.restore(
                id = "exp-1",
                accountId = "src-1",
                amount = Money.of(BigDecimal("200.00"), Currency.COP),
                date = this.transferDate,
                categoryId = "cat-transfer",
                description = "Transferencia a Efectivo",
                createdAt = fixedInstant,
                tagIds = emptyList(),
                gmfTax = null
            ),
            income = Income.restore(
                id = "inc-1",
                accountId = "dst-1",
                amount = Money.of(BigDecimal("200.00"), Currency.COP),
                date = this.transferDate,
                categoryId = "cat-transfer",
                description = "Transferencia desde Ahorros",
                createdAt = fixedInstant
            ),
            createdAt = fixedInstant
        )
    }

    @Test
    fun `given the save throws, when saving a transfer, then the prior state is restored and reported`() = runTest {
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(savingsAccount, cashAccount)))
        val viewModel = buildViewModel(originAccountId = "src-1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAmountChanged("200,00")
        val stateBeforeSaving = viewModel.uiState.value
        coEvery { transferCreator.create(any(), any(), any(), any(), any()) } throws IllegalStateException("falla")
        viewModel.save("2026-08-29")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(stateBeforeSaving is CreateTransferUiState.Idle)
        assertEquals(stateBeforeSaving, viewModel.uiState.value)
        viewModel.unexpectedFailureEvent.test {
            assertEquals(Unit, awaitItem())
            expectNoEvents()
        }
        verify(exactly = 1) { failureRecorder.record(any()) }
    }

    @Test
    fun `given a save that takes 60 seconds, when saving a transfer, then it completes with no failure`() = runTest {
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(savingsAccount, cashAccount)))
        val viewModel = buildViewModel(originAccountId = "src-1")
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { transferCreator.create(any(), any(), any(), any(), any()) } coAnswers {
            delay(60.seconds)
            Outcome.Success(transfer())
        }
        viewModel.onAmountChanged("200,00")
        viewModel.save("2026-08-29")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(NavigationTarget.AccountDetail("src-1"), viewModel.navigationEvent.first())
        verify(exactly = 0) { failureRecorder.record(any()) }
    }

    @Test
    fun `given an expected failure, when saving a transfer, then it shows as before and is not recorded`() =
        runTest {
            every { accountLister.list() } returns flowOf(Outcome.Success(listOf(savingsAccount, cashAccount)))
            coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
                TransferCreationError.StorageFailure("storage", "No se pudo guardar")
            )
            val viewModel = buildViewModel(originAccountId = "src-1")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onAmountChanged("200,00")
            viewModel.save("2026-08-29")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateTransferUiState.Error("No se pudo guardar"), viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }

    @Test
    fun `given loading throws, when loading the transfer form, then UnexpectedFailure is shown and recorded`() =
        runTest {
            every { accountLister.list() } returns flow<Outcome<List<Account>>> { error("falla") }
            val viewModel = buildViewModel(originAccountId = "src-1")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateTransferUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given no result within 10 seconds, when loading the transfer form, then UnexpectedFailure is shown`() =
        runTest {
            every { accountLister.list() } returns flow<Outcome<List<Account>>> { awaitCancellation() }
            val viewModel = buildViewModel(originAccountId = "src-1")
            testDispatcher.scheduler.advanceTimeBy(10.seconds)
            testDispatcher.scheduler.runCurrent()
            assertEquals(CreateTransferUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given UnexpectedFailure, when loading the transfer form again succeeds, then the normal content is shown`() =
        runTest {
            every { accountLister.list() } returns flow<Outcome<List<Account>>> { error("falla") }
            val viewModel = buildViewModel(originAccountId = "src-1")
            testDispatcher.scheduler.advanceUntilIdle()
            every { accountLister.list() } returns flowOf(Outcome.Success(listOf(savingsAccount, cashAccount)))
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is CreateTransferUiState.Idle)
        }

    @Test
    fun `given UnexpectedFailure, when loading the transfer form again fails, then it is shown and recorded twice`() =
        runTest {
            every { accountLister.list() } returns flow<Outcome<List<Account>>> { error("falla") }
            val viewModel = buildViewModel(originAccountId = "src-1")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateTransferUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 2) { failureRecorder.record(any()) }
        }

    @Test
    fun `given an expected failure, when loading the transfer form, then it shows as before and is not recorded`() =
        runTest {
            every { accountLister.list() } returns flowOf(Outcome.Failure(StorageError("DB error")))
            val viewModel = buildViewModel(originAccountId = "src-1")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CreateTransferUiState.Error("Error al acceder a los datos"), viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTransferViewModelGmfTest {

    private val transferCreator = mockk<TransferCreator>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val accountLister = mockk<AccountLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val ahorros = AccountBuilder()
        .id("ahorros")
        .name("Ahorros")
        .initialBalance(Money.of(BigDecimal("1000000"), Currency.COP))
        .gmf(GmfTreatment.Taxed("cat-impuestos", listOf("4x1000")))
        .build()
    private val nomina = AccountBuilder().id("nomina").name("Nómina").build()
    private val corriente = AccountBuilder().id("corriente").name("Corriente").build()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(ahorros, nomina, corriente)))
        coEvery { transferCreator.create(any(), any(), any(), any(), any()) } returns Outcome.Failure(
            TransferCreationError.StorageFailure("storage", "No se pudo guardar")
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given no source chosen yet, when the form loads, then the tax option is not shown`() = runTest {
        val viewModel = loadedViewModel(null)

        assertFalse((viewModel.uiState.value as CreateTransferUiState.Idle).showsGmfOption)
    }

    @Test
    fun `given an exempt source, when it is chosen, then the tax option is not shown`() = runTest {
        val viewModel = loadedViewModel(null)

        viewModel.onSourceSelected("nomina")

        assertFalse((viewModel.uiState.value as CreateTransferUiState.Idle).showsGmfOption)
    }

    @Test
    fun `given Ahorros not exempt, when it is chosen as the source, then the option appears on`() = runTest {
        val viewModel = loadedViewModel(null)

        viewModel.onSourceSelected("ahorros")

        val state = viewModel.uiState.value as CreateTransferUiState.Idle
        assertTrue(state.showsGmfOption)
        assertTrue(state.chargeGmf)
    }

    @Test
    fun `given Ahorros as source and 200000 typed, when the option is on, then the tax line reads 800`() = runTest {
        val viewModel = loadedViewModel("ahorros")

        viewModel.onAmountChanged("200000")

        assertEquals("4x1000: $800,00", (viewModel.uiState.value as CreateTransferUiState.Idle).gmfTaxLine)
    }

    @Test
    fun `given Ahorros as source and 200000 typed, when turning the option off, then no tax line`() = runTest {
        val viewModel = loadedViewModel("ahorros")
        viewModel.onAmountChanged("200000")

        viewModel.onChargeGmfChanged(false)

        val state = viewModel.uiState.value as CreateTransferUiState.Idle
        assertFalse(state.chargeGmf)
        assertNull(state.gmfTaxLine)
    }

    @Test
    fun `given the option turned off, when choosing the source again, then the option is back on`() = runTest {
        val viewModel = loadedViewModel("ahorros")
        viewModel.onChargeGmfChanged(false)

        viewModel.onSourceSelected("ahorros")

        assertTrue((viewModel.uiState.value as CreateTransferUiState.Idle).chargeGmf)
    }

    @Test
    fun `given the option turned off, when saving, then the transfer is created without charging the tax`() = runTest {
        val viewModel = loadedViewModel("ahorros")
        viewModel.onDestinationSelected("corriente")
        viewModel.onAmountChanged("200000")
        viewModel.onChargeGmfChanged(false)

        viewModel.save("2026-08-29")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { transferCreator.create("ahorros", "corriente", "200000", "2026-08-29", chargeGmf = false) }
    }

    @Test
    fun `given the option on, when saving, then the transfer is created charging the tax`() = runTest {
        val viewModel = loadedViewModel("ahorros")
        viewModel.onDestinationSelected("corriente")
        viewModel.onAmountChanged("200000")

        viewModel.save("2026-08-29")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { transferCreator.create("ahorros", "corriente", "200000", "2026-08-29", chargeGmf = true) }
    }

    @Test
    fun `given an exempt source, when saving, then the transfer is created without charging the tax`() = runTest {
        val viewModel = loadedViewModel("nomina")
        viewModel.onDestinationSelected("corriente")
        viewModel.onAmountChanged("200000")

        viewModel.save("2026-08-29")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { transferCreator.create("nomina", "corriente", "200000", "2026-08-29", chargeGmf = false) }
    }

    private fun loadedViewModel(originAccountId: String?): CreateTransferViewModel {
        val viewModel = CreateTransferViewModel(
            originAccountId,
            transferCreator,
            accountLister,
            failureRecorder,
            testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }
}
