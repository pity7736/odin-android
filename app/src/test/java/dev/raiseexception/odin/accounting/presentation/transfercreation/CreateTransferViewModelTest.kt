package dev.raiseexception.odin.accounting.presentation.transfercreation

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.TransferCreator
import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transfer
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class CreateTransferViewModelTest {

    private val transferCreator = mockk<TransferCreator>()
    private val accountLister = mockk<AccountLister>()
    private val testDispatcher = StandardTestDispatcher()
    private val transferDate = LocalDate.parse("2026-08-29")
    private val sourceAccountId = "src-1"
    private val sourceAccount = AccountBuilder()
        .id("src-1")
        .name("Ahorros")
        .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
        .build()
    private val destinationAccount = AccountBuilder()
        .id("dst-1")
        .name("Efectivo")
        .initialBalance(Money.of(BigDecimal("500.00"), Currency.COP))
        .build()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CreateTransferViewModel(
        preselectedSourceAccountId = sourceAccountId,
        transferCreator = transferCreator,
        accountLister = accountLister,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `given init, when accounts load, then emits Idle with all accounts and preselected source`() =
        runTest {
            every { accountLister.list() } returns flowOf(
                Outcome.Success(listOf(sourceAccount, destinationAccount))
            )
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(CreateTransferUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as CreateTransferUiState.Idle
                assertEquals(2, state.accounts.size)
                assertEquals(sourceAccountId, state.selectedSourceAccountId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given accounts load fails, when init, then emits Error`() = runTest {
        every { accountLister.list() } returns flowOf(
            Outcome.Failure(StorageError("DB error"))
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(CreateTransferUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as CreateTransferUiState.Error
            assertNotNull(state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given Idle state, when saving valid transfer, then navigates to account detail`() =
        runTest {
            every { accountLister.list() } returns flowOf(
                Outcome.Success(listOf(sourceAccount, destinationAccount))
            )
            val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
            coEvery {
                transferCreator.create(
                    sourceAccountId = "src-1",
                    destinationAccountId = "dst-1",
                    amount = "200.00",
                    date = "2026-08-29"
                )
            } returns Outcome.Success(
                Transfer.restore(
                    id = "transfer-1",
                    expense = Expense.restore(
                        id = "exp-1",
                        accountId = "src-1",
                        amount = Money.of(BigDecimal("200.00"), Currency.COP),
                        date = transferDate,
                        categoryId = "cat-transfer",
                        description = "Transferencia a Efectivo",
                        createdAt = fixedInstant
                    ),
                    income = Income.restore(
                        id = "inc-1",
                        accountId = "dst-1",
                        amount = Money.of(BigDecimal("200.00"), Currency.COP),
                        date = transferDate,
                        categoryId = "cat-transfer",
                        description = "Transferencia desde Ahorros",
                        createdAt = fixedInstant
                    ),
                    createdAt = fixedInstant
                )
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("src-1", "dst-1", "200.00", "2026-08-29")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.navigationEvent.test {
                assertEquals(NavigationTarget.AccountDetail("src-1"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given Idle state, when saving with validation error, then emits ValidationError`() =
        runTest {
            every { accountLister.list() } returns flowOf(
                Outcome.Success(listOf(sourceAccount, destinationAccount))
            )
            coEvery {
                transferCreator.create(any(), any(), any(), any())
            } returns Outcome.Failure(
                TransferCreationError.InvalidInput(
                    amountError = "El monto debe ser mayor que cero.",
                    dateError = null,
                    sourceAccountError = null,
                    destinationAccountError = null
                )
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("src-1", "dst-1", "0", "2026-08-29")
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.test {
                val state = awaitItem() as CreateTransferUiState.ValidationError
                assertNotNull(state.amountError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given Saving state, when save called again, then ignores duplicate`() =
        runTest {
            every { accountLister.list() } returns flowOf(
                Outcome.Success(listOf(sourceAccount, destinationAccount))
            )
            val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
            coEvery {
                transferCreator.create(any(), any(), any(), any())
            } returns Outcome.Success(
                Transfer.restore(
                    id = "transfer-1",
                    expense = Expense.restore(
                        id = "exp-1",
                        accountId = "src-1",
                        amount = Money.of(BigDecimal("200.00"), Currency.COP),
                        date = transferDate,
                        categoryId = "cat-transfer",
                        description = "Transferencia a Efectivo",
                        createdAt = fixedInstant
                    ),
                    income = Income.restore(
                        id = "inc-1",
                        accountId = "dst-1",
                        amount = Money.of(BigDecimal("200.00"), Currency.COP),
                        date = transferDate,
                        categoryId = "cat-transfer",
                        description = "Transferencia desde Ahorros",
                        createdAt = fixedInstant
                    ),
                    createdAt = fixedInstant
                )
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.save("src-1", "dst-1", "200.00", "2026-08-29")
            viewModel.save("src-1", "dst-1", "200.00", "2026-08-29")
            testDispatcher.scheduler.advanceUntilIdle()
            coVerify(exactly = 1) { transferCreator.create(any(), any(), any(), any()) }
        }
}
