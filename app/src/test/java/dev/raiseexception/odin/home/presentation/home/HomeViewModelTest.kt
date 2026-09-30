package dev.raiseexception.odin.home.presentation.home

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.home.application.usecase.HomeAccountEntry
import dev.raiseexception.odin.home.application.usecase.HomeSummary
import dev.raiseexception.odin.home.application.usecase.HomeSummaryLoader
import dev.raiseexception.odin.home.application.usecase.RecentTransaction
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val homeSummaryLoader = mockk<HomeSummaryLoader>()
    private val testDispatcher = StandardTestDispatcher()

    private val storageError = object : DomainError {
        override val internalMessage = "Storage error"
        override val externalMessage = "Error interno"
    }

    private val savingsEntry = HomeAccountEntry.MoneyAccountEntry(
        id = "acc-1",
        name = "Ahorros",
        type = AccountType.SAVINGS,
        balance = pesos("5000000.00")
    )

    private val visaEntry = HomeAccountEntry.CreditCardEntry(
        id = "card-1",
        name = "Visa",
        debt = pesos("800000.00"),
        availableCredit = pesos("2200000.00")
    )

    private val emptySummary = HomeSummary(
        balanceTotals = emptyList(),
        debtTotals = emptyList(),
        entries = emptyList(),
        hasMoreEntries = false,
        recentTransactions = emptyList(),
        canTransfer = false
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HomeViewModel(homeSummaryLoader, testDispatcher)

    @Test
    fun `given a summary with entries, when initialized, then emits Content with every summary field`() = runTest {
        val salary = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = pesos("100.00"),
            date = LocalDate.parse("2026-09-10"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-09-10T10:00:00Z")
        )
        val recentTransactions = listOf(RecentTransaction(salary, "Ahorros"))
        val summary = HomeSummary(
            balanceTotals = listOf(pesos("5000000.00")),
            debtTotals = listOf(pesos("800000.00")),
            entries = listOf(savingsEntry, visaEntry),
            hasMoreEntries = true,
            recentTransactions = recentTransactions,
            canTransfer = true
        )
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(summary))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(
                HomeUiState.Content(
                    balanceTotals = listOf(pesos("5000000.00")),
                    debtTotals = listOf(pesos("800000.00")),
                    accounts = listOf(savingsEntry, visaEntry),
                    hasMoreAccounts = true,
                    recentTransactions = recentTransactions,
                    canTransfer = true
                ),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a summary with no entries, when initialized, then emits Empty`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(emptySummary))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a summary with credit cards and no money accounts, when initialized, then emits Content`() = runTest {
        val summary = emptySummary.copy(
            debtTotals = listOf(pesos("800000.00")),
            entries = listOf(visaEntry)
        )
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(summary))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val content = viewModel.uiState.value as HomeUiState.Content
        assertTrue(content.balanceTotals.isEmpty())
        assertEquals(listOf(pesos("800000.00")), content.debtTotals)
        assertEquals(listOf(visaEntry), content.accounts)
    }

    @Test
    fun `given the summary fails to load, when initialized, then emits Error`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Failure(storageError))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as HomeUiState.Error
            assertEquals("Error al cargar la información", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given content state, when onAccountSelected, then emits AccountDetail navigation`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(summaryWith(savingsEntry)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            viewModel.onAccountSelected("acc-1")
            val event = awaitItem()
            assertTrue(event is HomeNavigationTarget.AccountDetail)
            assertEquals("acc-1", (event as HomeNavigationTarget.AccountDetail).accountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given content state, when onTransactionSelected, then emits TransactionDetail navigation`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(summaryWith(savingsEntry)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            viewModel.onTransactionSelected("txn-1")
            val event = awaitItem()
            assertTrue(event is HomeNavigationTarget.TransactionDetail)
            assertEquals("txn-1", (event as HomeNavigationTarget.TransactionDetail).transactionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given empty state, when onCreateAccountSelected, then emits AccountCreate navigation`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(emptySummary))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            viewModel.onCreateAccountSelected()
            val event = awaitItem()
            assertTrue(event is HomeNavigationTarget.AccountCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given content state, when income shortcut selected, then emits income create target`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(summaryWith(savingsEntry)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            viewModel.onIncomeShortcutSelected()
            val event = awaitItem()
            assertTrue(event is HomeNavigationTarget.IncomeCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given content state, when expense shortcut selected, then emits expense create target`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(summaryWith(savingsEntry)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            viewModel.onExpenseShortcutSelected()
            val event = awaitItem()
            assertTrue(event is HomeNavigationTarget.ExpenseCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given content state, when transfer shortcut selected, then emits transfer create target`() = runTest {
        every { homeSummaryLoader.load() } returns flowOf(Outcome.Success(summaryWith(savingsEntry)))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            viewModel.onTransferShortcutSelected()
            val event = awaitItem()
            assertTrue(event is HomeNavigationTarget.TransferCreate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun summaryWith(entry: HomeAccountEntry): HomeSummary = emptySummary.copy(entries = listOf(entry))

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}
