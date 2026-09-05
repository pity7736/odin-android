package dev.raiseexception.odin.home.presentation.home

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.home.application.usecase.RecentTransactionLister
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val accountLister = mockk<AccountLister>()
    private val recentTransactionLister = RecentTransactionLister()
    private val testDispatcher = StandardTestDispatcher()

    private val storageError = object : DomainError {
        override val internalMessage = "Storage error"
        override val externalMessage = "Error interno"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HomeViewModel(accountLister, recentTransactionLister, testDispatcher)

    @Test
    fun `given accounts, when initialized, then emits Content with total balances by currency`() = runTest {
        val copAccount = AccountBuilder()
            .id("acc-1")
            .name("Ahorros COP")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()
        val usdAccount = AccountBuilder()
            .id("acc-2")
            .name("Ahorros USD")
            .initialBalance(Money.of(BigDecimal("500.00"), Currency.USD))
            .build()
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(listOf(copAccount, usdAccount)))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as HomeUiState.Content
            assertEquals(2, content.totalBalances.size)
            val copBalance = content.totalBalances.first { it.currency == Currency.COP }
            val usdBalance = content.totalBalances.first { it.currency == Currency.USD }
            assertEquals(BigDecimal("1000.00"), copBalance.amount)
            assertEquals(BigDecimal("500.00"), usdBalance.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given accounts with transactions, when initialized, then emits Content with up to 3 accounts`() = runTest {
        val accounts = (1..4).map { index ->
            AccountBuilder().id("acc-$index").name("Cuenta $index").build()
        }
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(accounts))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as HomeUiState.Content
            assertEquals(3, content.accounts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given more than 3 accounts, when initialized, then Content has hasMoreAccounts true`() = runTest {
        val accounts = (1..4).map { index ->
            AccountBuilder().id("acc-$index").name("Cuenta $index").build()
        }
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(accounts))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as HomeUiState.Content
            assertTrue(content.hasMoreAccounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given 3 or fewer accounts, when initialized, then Content has hasMoreAccounts false`() = runTest {
        val accounts = (1..3).map { index ->
            AccountBuilder().id("acc-$index").name("Cuenta $index").build()
        }
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(accounts))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as HomeUiState.Content
            assertFalse(content.hasMoreAccounts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given accounts with transactions, when initialized, then emits Content with recent transactions`() = runTest {
        val income = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("100.00"), Currency.COP),
            date = LocalDate.parse("2026-01-01"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-01-01T10:00:00Z")
        )
        val account = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .incomes(listOf(income))
            .build()
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(listOf(account)))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as HomeUiState.Content
            assertEquals(1, content.recentTransactions.size)
            assertEquals("inc-1", content.recentTransactions[0].transaction.id)
            assertEquals("Ahorros", content.recentTransactions[0].accountName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given accounts but no transactions, when initialized, then emits Content with empty transactions`() = runTest {
        val account = AccountBuilder().id("acc-1").name("Ahorros").build()
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(listOf(account)))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as HomeUiState.Content
            assertTrue(content.recentTransactions.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given no accounts, when initialized, then emits Empty`() = runTest {
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(emptyList()))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(HomeUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given repository failure, when initialized, then emits Error`() = runTest {
        every { accountLister.list(any()) } returns flowOf(Outcome.Failure(storageError))
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
        val account = AccountBuilder().id("acc-1").name("Ahorros").build()
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(listOf(account)))
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
        val account = AccountBuilder().id("acc-1").name("Ahorros").build()
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(listOf(account)))
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
        every { accountLister.list(any()) } returns flowOf(Outcome.Success(emptyList()))
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
    fun `given accounts in different currencies, when initialized, then emits one total per currency`() = runTest {
        val copAccount1 = AccountBuilder()
            .id("acc-1")
            .name("Ahorros COP")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()
        val copAccount2 = AccountBuilder()
            .id("acc-2")
            .name("Corriente COP")
            .initialBalance(Money.of(BigDecimal("2000.00"), Currency.COP))
            .build()
        val usdAccount = AccountBuilder()
            .id("acc-3")
            .name("Ahorros USD")
            .initialBalance(Money.of(BigDecimal("500.00"), Currency.USD))
            .build()
        every { accountLister.list(any()) } returns flowOf(
            Outcome.Success(listOf(copAccount1, copAccount2, usdAccount))
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as HomeUiState.Content
            assertEquals(2, content.totalBalances.size)
            val copTotal = content.totalBalances.first { it.currency == Currency.COP }
            val usdTotal = content.totalBalances.first { it.currency == Currency.USD }
            assertEquals(BigDecimal("3000.00"), copTotal.amount)
            assertEquals(BigDecimal("500.00"), usdTotal.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
