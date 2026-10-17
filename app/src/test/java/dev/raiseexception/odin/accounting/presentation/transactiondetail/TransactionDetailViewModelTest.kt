package dev.raiseexception.odin.accounting.presentation.transactiondetail

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.TransactionDeleter
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.TransactionDeletionError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.TagBuilder
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeGreen
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {

    private val transactionFinder = mockk<TransactionFinder>()
    private val transactionDeleter = mockk<TransactionDeleter>()
    private val failureRecorder = mockk<FailureRecorder>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private val transactionId = "test-tx-id"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        TransactionDetailViewModel(
            transactionId,
            transactionFinder,
            transactionDeleter,
            failureRecorder,
            testDispatcher
        )

    @Test
    fun `given a loading state, when the view model initializes, then emits loading`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Success(incomeDetail())
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given an existing income, when observing, then emits content with income styling`() = runTest {
        val detail = incomeDetail()
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals("+$1.000,00", state.formattedAmount)
            assertEquals(IncomeGreen, state.amountColor)
            assertEquals("14 de septiembre de 2026", state.formattedDate)
            assertEquals("Salario", state.categoryName)
            assertEquals("Ahorros", state.accountName)
            assertEquals("Pago mensual", state.description)
            assertEquals(true, state.isIncome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given an existing expense, when observing, then emits content with expense styling`() = runTest {
        val detail = expenseDetail()
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals("-$500,00", state.formattedAmount)
            assertEquals(ExpenseRed, state.amountColor)
            assertEquals("15 de septiembre de 2026", state.formattedDate)
            assertEquals("Alimentación", state.categoryName)
            assertEquals("Efectivo", state.accountName)
            assertEquals("Mercado semanal", state.description)
            assertEquals(false, state.isIncome)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a missing transaction, when observing, then emits not found`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Failure(
                TransactionLookupError.NotFound(
                    internalMessage = "Transaction with id $transactionId not found",
                    externalMessage = "Transacción no encontrada"
                )
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(TransactionDetailUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a storage error, when observing, then emits error with external message`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Failure(
                StorageError(
                    internalMessage = "Database error",
                    externalMessage = "Error al acceder a los datos"
                )
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Error
            assertEquals("Error al acceder a los datos", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a transaction with empty description, when observing, then emits content with empty string`() = runTest {
        val detail = TransactionDetail(
            transaction = Income.restore(
                id = transactionId,
                accountId = "acc-1",
                amount = Money.of(BigDecimal("1000.00"), Currency.COP),
                date = LocalDate.parse("2026-09-14"),
                categoryId = "cat-1",
                description = "",
                createdAt = Instant.parse("2026-09-14T10:00:00Z")
            ),
            categoryName = "Salario",
            accountName = "Ahorros",
            isTransfer = false,
            accountType = AccountType.SAVINGS,
            tags = emptyList()
        )
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals("", state.description)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a regular expense, when loaded, then Content isEditable is true`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(expenseDetail()))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals(true, state.isEditable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given the expense side of a transfer, when loaded, then Content isEditable is false`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Success(expenseDetail().copy(isTransfer = true))
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals(false, state.isEditable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a plain income, when loaded, then Content isEditable is true`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(incomeDetail()))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(TransactionDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as TransactionDetailUiState.Content
            assertEquals(true, state.isEditable)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given the income side of a transfer, when loaded, then Content isEditable is false`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(
            Outcome.Success(incomeDetail().copy(isTransfer = true))
        )
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, (viewModel.uiState.value as TransactionDetailUiState.Content).isEditable)
    }

    @Test
    fun `given a payment received by a credit card, when loaded, then Content isEditable is false`() = runTest {
        val detail = incomeDetail().copy(accountName = "Visa", isTransfer = true, accountType = AccountType.CREDIT_CARD)
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, (viewModel.uiState.value as TransactionDetailUiState.Content).isEditable)
    }

    @Test
    fun `given a payment on a credit card, when observing, then it is named Pago with income styling`() = runTest {
        val detail = incomeDetail().copy(accountName = "Visa", accountType = AccountType.CREDIT_CARD)
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as TransactionDetailUiState.Content
        assertEquals("Pago", state.typeLabel)
        assertEquals("+$1.000,00", state.formattedAmount)
        assertEquals(IncomeGreen, state.amountColor)
    }

    @Test
    fun `given an income on a savings account, when observing, then it is named Ingreso`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(incomeDetail()))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Ingreso", (viewModel.uiState.value as TransactionDetailUiState.Content).typeLabel)
    }

    @Test
    fun `given the money side of a card payment, when observing, then it is named Gasto`() = runTest {
        val detail = expenseDetail().copy(
            categoryName = "Transferencia",
            accountName = "Ahorros",
            isTransfer = true,
            accountType = AccountType.SAVINGS
        )
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Gasto", (viewModel.uiState.value as TransactionDetailUiState.Content).typeLabel)
    }

    @Test
    fun `given an expense on a credit card, when observing, then it is named Gasto`() = runTest {
        val detail = expenseDetail().copy(accountName = "Visa", accountType = AccountType.CREDIT_CARD)
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(detail))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Gasto", (viewModel.uiState.value as TransactionDetailUiState.Content).typeLabel)
    }

    @Test
    fun `given an expense with tags, when observing, then Content carries the tag names in order`() = runTest {
        val tags = listOf(TagBuilder().name("Comida").build(), TagBuilder().name("Nala").build())
        every { transactionFinder.find(transactionId) } returns
            flowOf(Outcome.Success(expenseDetail().copy(tags = tags)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as TransactionDetailUiState.Content
        assertEquals(listOf("Comida", "Nala"), state.tagNames)
    }

    @Test
    fun `given an expense without tags, when observing, then Content has no tag names`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(expenseDetail()))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as TransactionDetailUiState.Content
        assertTrue(state.tagNames.isEmpty())
    }

    @Test
    fun `given an income, when observing, then Content has no tag names`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(incomeDetail()))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as TransactionDetailUiState.Content
        assertTrue(state.tagNames.isEmpty())
    }

    private fun incomeDetail(): TransactionDetail =
        TransactionDetail(
            transaction = Income.restore(
                id = transactionId,
                accountId = "acc-1",
                amount = Money.of(BigDecimal("1000.00"), Currency.COP),
                date = LocalDate.parse("2026-09-14"),
                categoryId = "cat-1",
                description = "Pago mensual",
                createdAt = Instant.parse("2026-09-14T10:00:00Z")
            ),
            categoryName = "Salario",
            accountName = "Ahorros",
            isTransfer = false,
            accountType = AccountType.SAVINGS,
            tags = emptyList()
        )

    private fun expenseDetail(): TransactionDetail =
        TransactionDetail(
            transaction = Expense.restore(
                id = transactionId,
                accountId = "acc-2",
                amount = Money.of(BigDecimal("500.00"), Currency.COP),
                date = LocalDate.parse("2026-09-15"),
                categoryId = "cat-2",
                description = "Mercado semanal",
                createdAt = Instant.parse("2026-09-15T10:00:00Z"),
                tagIds = emptyList()
            ),
            categoryName = "Alimentación",
            accountName = "Efectivo",
            isTransfer = false,
            accountType = AccountType.CASH,
            tags = emptyList()
        )

    @Test
    fun `given loading throws, when loading the movement, then UnexpectedFailure is shown and recorded`() =
        runTest {
            every { transactionFinder.find(transactionId) } returns flow<Outcome<TransactionDetail>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(TransactionDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given no result within 10 seconds, when loading the movement, then UnexpectedFailure is shown`() =
        runTest {
            every { transactionFinder.find(transactionId) } returns
                flow<Outcome<TransactionDetail>> { awaitCancellation() }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceTimeBy(10.seconds)
            testDispatcher.scheduler.runCurrent()
            assertEquals(TransactionDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given UnexpectedFailure, when loading the movement again succeeds, then the normal content is shown`() =
        runTest {
            every { transactionFinder.find(transactionId) } returns flow<Outcome<TransactionDetail>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(incomeDetail()))
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is TransactionDetailUiState.Content)
        }

    @Test
    fun `given UnexpectedFailure, when loading the movement again fails, then it is shown and recorded twice`() =
        runTest {
            every { transactionFinder.find(transactionId) } returns flow<Outcome<TransactionDetail>> { error("falla") }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.retry()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(TransactionDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 2) { failureRecorder.record(any()) }
        }

    @Test
    fun `given an expected failure, when loading the movement, then it shows as before and is not recorded`() =
        runTest {
            every {
                transactionFinder.find(transactionId)
            } returns
                flowOf(Outcome.Failure(TransactionLookupError.NotFound("Not found", "No existe")))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(TransactionDetailUiState.NotFound, viewModel.uiState.value)
            verify(exactly = 0) { failureRecorder.record(any()) }
        }

    @Test
    fun `given a first result then a failure, when loading the movement, then UnexpectedFailure is shown`() =
        runTest {
            every { transactionFinder.find(transactionId) } returns flow<Outcome<TransactionDetail>> {
                emit(Outcome.Success(incomeDetail()))
                error("falla")
            }
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(TransactionDetailUiState.UnexpectedFailure, viewModel.uiState.value)
            verify(exactly = 1) { failureRecorder.record(any()) }
        }

    @Test
    fun `given an expense, when deleting it, then it is deleting, stops watching, and announces Gasto eliminado`() =
        runTest {
            val detailFlow = MutableStateFlow<Outcome<TransactionDetail>>(Outcome.Success(expenseDetail()))
            every { transactionFinder.find(transactionId) } returns detailFlow
            coEvery { transactionDeleter.delete(transactionId) } returns Outcome.Success(Unit)
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.deletedEvent.test {
                viewModel.delete()
                assertEquals(true, (viewModel.uiState.value as TransactionDetailUiState.Content).isDeleting)
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals("Gasto eliminado", awaitItem())
                detailFlow.value = Outcome.Failure(TransactionLookupError.NotFound("Not found", "No existe"))
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(true, (viewModel.uiState.value as TransactionDetailUiState.Content).isDeleting)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given an income, when deleting it, then announces Ingreso eliminado`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(incomeDetail()))
        coEvery { transactionDeleter.delete(transactionId) } returns Outcome.Success(Unit)
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.deletedEvent.test {
            viewModel.delete()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("Ingreso eliminado", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a deletion that breaks the history, when deleting, then shows the message and resumes watching`() =
        runTest {
            val detailFlow = MutableStateFlow<Outcome<TransactionDetail>>(Outcome.Success(incomeDetail()))
            every { transactionFinder.find(transactionId) } returns detailFlow
            coEvery { transactionDeleter.delete(transactionId) } returns Outcome.Failure(
                TransactionDeletionError.HistoryBreach("El saldo de la cuenta quedaría negativo el 8 de marzo.")
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.deletionMessageEvent.test {
                viewModel.delete()
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals("El saldo de la cuenta quedaría negativo el 8 de marzo.", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(false, (viewModel.uiState.value as TransactionDetailUiState.Content).isDeleting)
            assertWatchingResumes(viewModel, detailFlow)
        }

    @Test
    fun `given the deletion fails in storage, when deleting, then shows the storage message and resumes watching`() =
        runTest {
            val detailFlow = MutableStateFlow<Outcome<TransactionDetail>>(Outcome.Success(expenseDetail()))
            every { transactionFinder.find(transactionId) } returns detailFlow
            coEvery { transactionDeleter.delete(transactionId) } returns Outcome.Failure(
                TransactionDeletionError.StorageFailure(
                    internalMessage = "disk I/O error",
                    externalMessage = "No se pudo eliminar el gasto. Inténtalo de nuevo."
                )
            )
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.deletionMessageEvent.test {
                viewModel.delete()
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals("No se pudo eliminar el gasto. Inténtalo de nuevo.", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(false, (viewModel.uiState.value as TransactionDetailUiState.Content).isDeleting)
            assertWatchingResumes(viewModel, detailFlow)
        }

    @Test
    fun `given deleting throws, when deleting, then signals the unexpected failure, restores and resumes watching`() =
        runTest {
            val detailFlow = MutableStateFlow<Outcome<TransactionDetail>>(Outcome.Success(expenseDetail()))
            every { transactionFinder.find(transactionId) } returns detailFlow
            coEvery { transactionDeleter.delete(transactionId) } throws IllegalStateException("falla")
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.unexpectedFailureEvent.test {
                viewModel.delete()
                testDispatcher.scheduler.advanceUntilIdle()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(false, (viewModel.uiState.value as TransactionDetailUiState.Content).isDeleting)
            verify(exactly = 1) { failureRecorder.record(any()) }
            assertWatchingResumes(viewModel, detailFlow)
        }

    @Test
    fun `given a deletion in progress, when deleting again, then the second request is ignored`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(expenseDetail()))
        coEvery { transactionDeleter.delete(transactionId) } returns Outcome.Success(Unit)
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.delete()
        viewModel.delete()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { transactionDeleter.delete(transactionId) }
    }

    @Test
    fun `given a transfer side, when deleting, then nothing happens`() = runTest {
        every { transactionFinder.find(transactionId) } returns
            flowOf(Outcome.Success(expenseDetail().copy(isTransfer = true)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val stateBefore = viewModel.uiState.value
        viewModel.delete()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(stateBefore, viewModel.uiState.value)
        coVerify(exactly = 0) { transactionDeleter.delete(any()) }
    }

    @Test
    fun `given the movement is still loading, when deleting, then nothing happens`() = runTest {
        every { transactionFinder.find(transactionId) } returns flowOf(Outcome.Success(expenseDetail()))
        val viewModel = buildViewModel()
        viewModel.delete()
        assertEquals(TransactionDetailUiState.Loading, viewModel.uiState.value)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { transactionDeleter.delete(any()) }
    }

    @Test
    fun `given the movement was not found, when deleting, then nothing happens`() = runTest {
        every { transactionFinder.find(transactionId) } returns
            flowOf(Outcome.Failure(TransactionLookupError.NotFound("Not found", "No existe")))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.delete()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(TransactionDetailUiState.NotFound, viewModel.uiState.value)
        coVerify(exactly = 0) { transactionDeleter.delete(any()) }
    }

    @Test
    fun `given the movement failed to load, when deleting, then nothing happens`() = runTest {
        every { transactionFinder.find(transactionId) } returns
            flowOf(Outcome.Failure(StorageError("Database error", "Error al acceder a los datos")))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.delete()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(TransactionDetailUiState.Error("Error al acceder a los datos"), viewModel.uiState.value)
        coVerify(exactly = 0) { transactionDeleter.delete(any()) }
    }

    private fun assertWatchingResumes(
        viewModel: TransactionDetailViewModel,
        detailFlow: MutableStateFlow<Outcome<TransactionDetail>>
    ) {
        val current = (detailFlow.value as Outcome.Success).value
        detailFlow.value = Outcome.Success(current.copy(categoryName = "Categoría nueva"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Categoría nueva", (viewModel.uiState.value as TransactionDetailUiState.Content).categoryName)
    }
}
