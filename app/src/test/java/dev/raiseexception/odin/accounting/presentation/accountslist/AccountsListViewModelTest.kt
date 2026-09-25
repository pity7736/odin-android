package dev.raiseexception.odin.accounting.presentation.accountslist

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsListViewModelTest {

    private val accountLister = mockk<AccountLister>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val criteriaWithTransactions = AccountCriteria(includeIncomes = true, includeExpenses = true)

    private fun buildViewModel() = AccountsListViewModel(accountLister, testDispatcher)

    private fun account(id: String, name: String) = AccountBuilder().id(id).name(name).build()

    private val storageError = object : DomainError {
        override val internalMessage = "Storage error"
        override val externalMessage = "Error interno"
    }

    @Test
    fun `given lister emits empty list, when initialized, then ui state is Empty`() = runTest {
        every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(emptyList()))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AccountsListUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given lister emits accounts, when initialized, then ui state is Content with accounts`() = runTest {
        val savings = account("aaa", "Ahorros")
        val checking = account("bbb", "Corriente")
        val accounts = listOf(savings, checking)
        every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(accounts))
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
        every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(savings)))
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
    fun `given a credit card among accounts, when initialized, then the credit card is excluded`() = runTest {
        val savings = account("acc-1", "Ahorros")
        val creditCard = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .creditCard(
                creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
                debt = Money.of(BigDecimal("500000.00"), Currency.COP)
            )
            .build()
        every {
            accountLister.list(criteriaWithTransactions)
        } returns flowOf(Outcome.Success(listOf(savings, creditCard)))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as AccountsListUiState.Content
            assertEquals(1, content.accounts.size)
            assertEquals("acc-1", content.accounts.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given only a credit card, when initialized, then ui state is Empty`() = runTest {
        val creditCard = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .creditCard(
                creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
                debt = Money.of(BigDecimal("500000.00"), Currency.COP)
            )
            .build()
        every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(creditCard)))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AccountsListUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given lister emits failure, when initialized, then ui state is Error`() = runTest {
        every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Failure(storageError))
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
