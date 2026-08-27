package dev.raiseexception.odin.accounting.presentation.accountslist

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsListViewModelTest {

    private val accountRepository = mockk<AccountRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = AccountsListViewModel(accountRepository, testDispatcher)

    private fun account(id: String, name: String): Account = Account.restore(
        id = id,
        name = name,
        initialBalance = Money.of(BigDecimal("100.00"), Currency.COP),
        type = AccountType.SAVINGS,
        description = "",
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    @Test
    fun `given repository emits empty list, when initialized, then ui state is Empty`() = runTest {
        every { accountRepository.getAll() } returns flowOf(emptyList())
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AccountsListUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given repository emits accounts, when initialized, then ui state is Content with accounts`() = runTest {
        val savings = account("aaa", "Ahorros")
        val checking = account("bbb", "Corriente")
        every { accountRepository.getAll() } returns flowOf(listOf(savings, checking))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as AccountsListUiState.Content
            assertEquals(2, content.accounts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given Content state, when an account is selected, then navigation event is AccountDetail`() = runTest {
        val savings = account("aaa", "Ahorros")
        every { accountRepository.getAll() } returns flowOf(listOf(savings))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigationEvent.test {
            viewModel.onAccountSelected("aaa")
            val event = awaitItem()
            assertTrue(event is AccountsListNavigationTarget.AccountDetail)
            assertEquals("aaa", (event as AccountsListNavigationTarget.AccountDetail).accountId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `given repository throws, when initialized, then ui state is Error`() = runTest {
        every { accountRepository.getAll() } returns flow { throw RuntimeException("Storage error") }
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountsListUiState.Error
            assertEquals("Error al cargar las cuentas", state.message)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
