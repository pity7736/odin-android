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

    private fun creditCard(id: String, name: String, creditLimit: String, initialDebt: String) = AccountBuilder()
        .id(id)
        .name(name)
        .creditCard(creditLimit = pesos(creditLimit), initialDebt = pesos(initialDebt))
        .build()

    private fun pesos(amount: String) = Money.of(BigDecimal(amount), Currency.COP)

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
            assertEquals(2, content.moneyAccounts.size)
            assertTrue(content.creditCards.isEmpty())
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
    fun `given a money account and a credit card, when initialized, then each appears in its own group`() = runTest {
        val savings = account("acc-1", "Ahorros")
        val creditCard = creditCard("card-1", "Visa", "3000000.00", "500000.00")
        every {
            accountLister.list(criteriaWithTransactions)
        } returns flowOf(Outcome.Success(listOf(savings, creditCard)))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as AccountsListUiState.Content
            assertEquals(listOf(savings), content.moneyAccounts)
            assertEquals(1, content.creditCards.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given only a credit card, when initialized, then ui state is Content with only the credit card group`() =
        runTest {
            val creditCard = creditCard("card-1", "Visa", "3000000.00", "500000.00")
            every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(creditCard)))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertEquals(AccountsListUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val content = awaitItem() as AccountsListUiState.Content
                assertTrue(content.moneyAccounts.isEmpty())
                assertEquals(1, content.creditCards.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a credit card with debt, when initialized, then its item shows debt and available credit`() = runTest {
        val creditCard = creditCard("card-1", "Visa", "3000000.00", "500000.00")
        every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(creditCard)))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as AccountsListUiState.Content
            val expectedItem = CreditCardItem(
                id = "card-1",
                name = "Visa",
                debt = pesos("500000.00"),
                availableCredit = pesos("2500000.00")
            )
            assertEquals(listOf(expectedItem), content.creditCards)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a credit card with an expense, when initialized, then its debt and available credit include it`() =
        runTest {
            val creditCard = AccountBuilder()
                .id("card-1")
                .name("Visa")
                .creditCard(creditLimit = pesos("3000000.00"), initialDebt = pesos("500000.00"))
                .withExpense(amount = "200000")
                .build()
            every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(creditCard)))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertEquals(AccountsListUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val content = awaitItem() as AccountsListUiState.Content
                val cardItem = content.creditCards.single()
                assertEquals(pesos("700000.00"), cardItem.debt)
                assertEquals(pesos("2300000.00"), cardItem.availableCredit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a card with an expense and a payment, when initialized, then its figures are net of the payment`() =
        runTest {
            val creditCard = AccountBuilder()
                .id("card-1")
                .name("Visa")
                .creditCard(creditLimit = pesos("3000000.00"), initialDebt = pesos("500000.00"))
                .withExpense(amount = "200000")
                .withIncome(amount = "150000")
                .build()
            every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(creditCard)))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val cardItem = (viewModel.uiState.value as AccountsListUiState.Content).creditCards.single()
            assertEquals(pesos("550000.00"), cardItem.debt)
            assertEquals(pesos("2450000.00"), cardItem.availableCredit)
        }

    @Test
    fun `given a credit card with no debt, when initialized, then available credit is the full limit`() = runTest {
        val creditCard = creditCard("card-1", "Visa", "3000000.00", "0")
        every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(creditCard)))
        val viewModel = buildViewModel()

        viewModel.uiState.test {
            assertEquals(AccountsListUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val content = awaitItem() as AccountsListUiState.Content
            val cardItem = content.creditCards.single()
            assertEquals(pesos("0"), cardItem.debt)
            assertEquals(pesos("3000000.00"), cardItem.availableCredit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a credit card whose debt equals its limit, when initialized, then available credit is zero`() =
        runTest {
            val creditCard = creditCard("card-1", "Visa", "3000000.00", "3000000.00")
            every { accountLister.list(criteriaWithTransactions) } returns flowOf(Outcome.Success(listOf(creditCard)))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertEquals(AccountsListUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val content = awaitItem() as AccountsListUiState.Content
                val cardItem = content.creditCards.single()
                assertEquals(pesos("3000000.00"), cardItem.debt)
                assertEquals(pesos("0"), cardItem.availableCredit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given interleaved accounts and credit cards, when initialized, then each group keeps the lister order`() =
        runTest {
            val savings = account("acc-1", "Ahorros")
            val visa = creditCard("card-1", "Visa", "3000000.00", "500000.00")
            val cash = account("acc-2", "Efectivo")
            val mastercard = creditCard("card-2", "Mastercard", "2000000.00", "100000.00")
            every {
                accountLister.list(criteriaWithTransactions)
            } returns flowOf(Outcome.Success(listOf(savings, visa, cash, mastercard)))
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                assertEquals(AccountsListUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val content = awaitItem() as AccountsListUiState.Content
                assertEquals(listOf(savings, cash), content.moneyAccounts)
                assertEquals(listOf("card-1", "card-2"), content.creditCards.map { it.id })
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
