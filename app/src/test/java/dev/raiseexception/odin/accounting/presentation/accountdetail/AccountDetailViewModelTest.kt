package dev.raiseexception.odin.accounting.presentation.accountdetail

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountTransactionLister
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    private val accountFinder = mockk<AccountFinder>()
    private val accountTransactionLister = AccountTransactionLister()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "test-account-id"
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
        AccountDetailViewModel(accountId, accountFinder, accountTransactionLister, testDispatcher)

    private fun clockAt(instant: String): Clock = object : Clock {
        override fun now(): Instant = Instant.parse(instant)
    }

    @Test
    fun `given an existing account, when the screen loads, then uiState is Content with the account`() = runTest {
        val savings = AccountBuilder().id(accountId).build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(savings)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.Content
            assertEquals(savings.id, state.account.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given the account is not found, when the screen loads, then uiState is NotFound`() = runTest {
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Failure(
            AccountLookupError.NotFound(
                internalMessage = "Not found",
                externalMessage = "Cuenta no encontrada"
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AccountDetailUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a storage failure, when the screen loads, then uiState is Error with a Spanish message`() = runTest {
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Failure(
            AccountLookupError.StorageFailure(
                internalMessage = "Storage error",
                externalMessage = "Error al cargar la cuenta"
            )
        )
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.Error
            assertEquals("Error al cargar la cuenta", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account with incomes, when loaded, then content state carries computed balance`() = runTest {
        val accountWithIncomes = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-08-28")
            .build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(accountWithIncomes)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.Content
            assertEquals(
                0,
                state.account.balance.amount.compareTo(BigDecimal("1500.00"))
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account, when loaded, then criteria includes both incomes and expenses`() = runTest {
        val savings = AccountBuilder().id(accountId).build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(savings)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem() is AccountDetailUiState.Content)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { accountFinder.find(accountId, AccountCriteria(includeIncomes = true, includeExpenses = true)) }
    }

    @Test
    fun `given account with transactions, when loaded, then content has all transactions and ALL filter`() = runTest {
        val account = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(account)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.Content
            assertEquals(TransactionFilter.ALL, state.activeFilter)
            assertEquals(2, state.transactions.size)
            assertTrue(state.transactions[0].transaction is Expense)
            assertTrue(state.transactions[1].transaction is Income)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account with transactions, when filter changed to INCOME, then shows only incomes`() = runTest {
        val account = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(account)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.INCOME)
            val state = awaitItem() as AccountDetailUiState.Content
            assertEquals(TransactionFilter.INCOME, state.activeFilter)
            assertEquals(1, state.transactions.size)
            assertTrue(state.transactions.all { it.transaction is Income })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account with transactions, when filter changed to EXPENSE, then shows only expenses`() = runTest {
        val account = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(account)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.EXPENSE)
            val state = awaitItem() as AccountDetailUiState.Content
            assertEquals(TransactionFilter.EXPENSE, state.activeFilter)
            assertEquals(1, state.transactions.size)
            assertTrue(state.transactions.all { it.transaction is Expense })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account with transactions, when filter reset to ALL, then shows all with running balances`() = runTest {
        val account = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(account)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.INCOME)
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.ALL)
            val state = awaitItem() as AccountDetailUiState.Content
            assertEquals(TransactionFilter.ALL, state.activeFilter)
            assertEquals(2, state.transactions.size)
            assertTrue(state.transactions.all { it.runningBalance != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account with no transactions, when loaded, then content has empty transactions`() = runTest {
        val account = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()
        coEvery { accountFinder.find(accountId, criteria) } returns Outcome.Success(account)
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.Content
            assertTrue(state.transactions.isEmpty())
            assertEquals(TransactionFilter.ALL, state.activeFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
