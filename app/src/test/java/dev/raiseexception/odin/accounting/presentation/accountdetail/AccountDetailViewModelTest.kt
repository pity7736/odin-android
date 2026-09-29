package dev.raiseexception.odin.accounting.presentation.accountdetail

import app.cash.turbine.test
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.AccountTransaction
import dev.raiseexception.odin.accounting.application.usecase.AccountTransactionLister
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelTest {

    private val accountFinder = mockk<AccountFinder>()
    private val accountLister = mockk<AccountLister>()
    private val accountTransactionLister = AccountTransactionLister()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "test-account-id"
    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { accountLister.list() } returns flowOf(Outcome.Success(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        AccountDetailViewModel(accountId, accountFinder, accountLister, accountTransactionLister, testDispatcher)

    private fun clockAt(instant: String): Clock = object : Clock {
        override fun now(): Instant = Instant.parse(instant)
    }

    @Test
    fun `given an existing account, when the screen loads, then uiState is Content with the account`() = runTest {
        val savings = AccountBuilder().id(accountId).build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(savings))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
            assertEquals(savings.id, state.account.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given the account is not found, when the screen loads, then uiState is NotFound`() = runTest {
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
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(AccountDetailUiState.NotFound, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a storage failure, when the screen loads, then uiState is Error with a Spanish message`() = runTest {
        every { accountFinder.find(accountId, criteria) } returns flowOf(
            Outcome.Failure(
                dev.raiseexception.odin.shared.domain.StorageError(
                    internalMessage = "Storage error",
                    externalMessage = "Error al cargar la cuenta"
                )
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
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(accountWithIncomes))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
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
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(savings))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem() is AccountDetailUiState.MoneyAccountContent)
            cancelAndIgnoreRemainingEvents()
        }
        verify { accountFinder.find(accountId, AccountCriteria(includeIncomes = true, includeExpenses = true)) }
    }

    @Test
    fun `given account with transactions, when loaded, then content has all transactions and ALL filter`() = runTest {
        val account = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(account))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
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
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(account))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.INCOME)
            val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
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
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(account))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.EXPENSE)
            val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
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
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(account))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.INCOME)
            awaitItem()
            viewModel.onFilterChanged(TransactionFilter.ALL)
            val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
            assertEquals(TransactionFilter.ALL, state.activeFilter)
            assertEquals(2, state.transactions.size)
            assertTrue(state.transactions.all { it.runningBalance != null })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given content state, when a transaction is selected, then emits transaction detail navigation`() = runTest {
        val savings = AccountBuilder().id(accountId).build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(savings))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(awaitItem() is AccountDetailUiState.MoneyAccountContent)
            cancelAndIgnoreRemainingEvents()
        }
        viewModel.onTransactionSelected("tx-123")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            val target = awaitItem()
            assertTrue(target is AccountDetailNavigationTarget.TransactionDetail)
            assertEquals("tx-123", (target as AccountDetailNavigationTarget.TransactionDetail).transactionId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given account with no transactions, when loaded, then content has empty transactions`() = runTest {
        val account = AccountBuilder()
            .id(accountId)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(account))
        val viewModel = buildViewModel()
        viewModel.uiState.test {
            assertEquals(AccountDetailUiState.Loading, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
            assertTrue(state.transactions.isEmpty())
            assertEquals(TransactionFilter.ALL, state.activeFilter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a money account with movements, when the screen loads, then shows its current and initial balance`() =
        runTest {
            val savingsAccount = AccountBuilder()
                .id(accountId)
                .initialBalance(Money.of(BigDecimal("1000000"), Currency.COP))
                .withIncome(amount = "500000", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
                .withExpense(amount = "200000", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(savingsAccount))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(AccountDetailUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as AccountDetailUiState.MoneyAccountContent
                assertEquals(Money.of(BigDecimal("1300000"), Currency.COP), state.account.balance)
                assertEquals(Money.of(BigDecimal("1000000"), Currency.COP), state.initialBalance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a credit card with debt, when the screen loads, then shows its debt, available credit and limit`() =
        runTest {
            val visaCard = AccountBuilder()
                .id(accountId)
                .name("Visa")
                .creditCard(
                    creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("500000"), Currency.COP)
                )
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(AccountDetailUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(
                    AccountDetailUiState.CreditCardContent(
                        CreditCardDetail(
                            name = "Visa",
                            debt = Money.of(BigDecimal("500000"), Currency.COP),
                            availableCredit = Money.of(BigDecimal("2500000"), Currency.COP),
                            creditLimit = Money.of(BigDecimal("3000000"), Currency.COP)
                        ),
                        canPay = false,
                        transactions = emptyList(),
                        activeFilter = TransactionFilter.ALL
                    ),
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a credit card with no debt, when the screen loads, then available credit equals the limit`() =
        runTest {
            val unusedCard = AccountBuilder()
                .id(accountId)
                .creditCard(
                    creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("0"), Currency.COP)
                )
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(unusedCard))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(AccountDetailUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as AccountDetailUiState.CreditCardContent
                assertEquals(Money.of(BigDecimal("0"), Currency.COP), state.creditCard.debt)
                assertEquals(Money.of(BigDecimal("3000000"), Currency.COP), state.creditCard.availableCredit)
                assertEquals(Money.of(BigDecimal("3000000"), Currency.COP), state.creditCard.creditLimit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a credit card whose debt equals its limit, when the screen loads, then available credit is zero`() =
        runTest {
            val maxedOutCard = AccountBuilder()
                .id(accountId)
                .creditCard(
                    creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("3000000"), Currency.COP)
                )
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(maxedOutCard))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(AccountDetailUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                val state = awaitItem() as AccountDetailUiState.CreditCardContent
                assertEquals(Money.of(BigDecimal("0"), Currency.COP), state.creditCard.availableCredit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a credit card with an expense, when the screen loads, then its figures include the expense`() =
        runTest {
            val visaCard = AccountBuilder()
                .id(accountId)
                .name("Visa")
                .creditCard(
                    creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("500000"), Currency.COP)
                )
                .withExpense(amount = "200000", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(AccountDetailUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(
                    AccountDetailUiState.CreditCardContent(
                        CreditCardDetail(
                            name = "Visa",
                            debt = Money.of(BigDecimal("700000"), Currency.COP),
                            availableCredit = Money.of(BigDecimal("2300000"), Currency.COP),
                            creditLimit = Money.of(BigDecimal("3000000"), Currency.COP)
                        ),
                        canPay = false,
                        transactions = listOf(
                            AccountTransaction(visaCard.expenses.single(), Money.of(BigDecimal("700000"), Currency.COP))
                        ),
                        activeFilter = TransactionFilter.ALL
                    ),
                    awaitItem()
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a credit card, when creating an expense, then emits create expense navigation for the card`() =
        runTest {
            val visaCard = AccountBuilder()
                .id(accountId)
                .creditCard(
                    creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("500000"), Currency.COP)
                )
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            val viewModel = buildViewModel()
            viewModel.uiState.test {
                assertEquals(AccountDetailUiState.Loading, awaitItem())
                testDispatcher.scheduler.advanceUntilIdle()
                assertTrue(awaitItem() is AccountDetailUiState.CreditCardContent)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.onCreateExpense()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.navigationEvent.test {
                assertEquals(AccountDetailNavigationTarget.CreateExpense(accountId), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given a card with an expense and a payment, when the screen loads, then shows the debt net of the payment`() =
        runTest {
            val visaCard = visaCard()
                .withExpense(amount = "200000", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
                .withIncome(amount = "100000", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value as AccountDetailUiState.CreditCardContent
            assertEquals(Money.of(BigDecimal("600000"), Currency.COP), state.creditCard.debt)
            assertEquals(Money.of(BigDecimal("2400000"), Currency.COP), state.creditCard.availableCredit)
        }

    @Test
    fun `given the user has a money account, when a card's details load, then the payment is offered`() = runTest {
        val visaCard = visaCard().build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(visaCard, savingsAccount())))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as AccountDetailUiState.CreditCardContent).canPay)
    }

    @Test
    fun `given the user has only credit cards, when a card's details load, then the payment is not offered`() =
        runTest {
            val visaCard = visaCard().build()
            val mastercard = AccountBuilder()
                .id("card-2")
                .creditCard(
                    creditLimit = Money.of(BigDecimal("1000000"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("0"), Currency.COP)
                )
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            every { accountLister.list() } returns flowOf(Outcome.Success(listOf(visaCard, mastercard)))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse((viewModel.uiState.value as AccountDetailUiState.CreditCardContent).canPay)
        }

    @Test
    fun `given a money account and a card with no debt, when the card's details load, then payment is offered`() =
        runTest {
            val unusedCard = AccountBuilder()
                .id(accountId)
                .creditCard(
                    creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
                    initialDebt = Money.of(BigDecimal("0"), Currency.COP)
                )
                .build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(unusedCard))
            every { accountLister.list() } returns flowOf(Outcome.Success(listOf(unusedCard, savingsAccount())))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertTrue((viewModel.uiState.value as AccountDetailUiState.CreditCardContent).canPay)
        }

    @Test
    fun `given only cards at first, when a money account appears later, then the payment becomes offered`() =
        runTest {
            val visaCard = visaCard().build()
            val listedAccounts = MutableStateFlow<Outcome<List<Account>>>(Outcome.Success(listOf(visaCard)))
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            every { accountLister.list() } returns listedAccounts
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val canPayBefore = (viewModel.uiState.value as AccountDetailUiState.CreditCardContent).canPay
            listedAccounts.value = Outcome.Success(listOf(visaCard, savingsAccount()))
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(canPayBefore)
            assertTrue((viewModel.uiState.value as AccountDetailUiState.CreditCardContent).canPay)
        }

    @Test
    fun `given the account list cannot load, when a card's details load, then the payment is not offered`() =
        runTest {
            val visaCard = visaCard().build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            every { accountLister.list() } returns flowOf(Outcome.Failure(StorageError("DB error")))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse((viewModel.uiState.value as AccountDetailUiState.CreditCardContent).canPay)
        }

    @Test
    fun `given a money account and other accounts, when its details load, then its state is unchanged`() = runTest {
        val savings = AccountBuilder().id(accountId).build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(savings))
        every { accountLister.list() } returns flowOf(Outcome.Success(listOf(savings, savingsAccount())))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            AccountDetailUiState.MoneyAccountContent(
                account = savings,
                initialBalance = Money.of(BigDecimal("100000.00"), Currency.COP),
                transactions = emptyList(),
                activeFilter = TransactionFilter.ALL,
            ),
            viewModel.uiState.value
        )
    }

    private fun visaCard(): AccountBuilder = AccountBuilder()
        .id(this.accountId)
        .name("Visa")
        .creditCard(
            creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
            initialDebt = Money.of(BigDecimal("500000"), Currency.COP)
        )

    private fun savingsAccount(): Account = AccountBuilder().id("savings-1").build()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModelCreditCardMovementsTest {

    private val accountFinder = mockk<AccountFinder>()
    private val accountLister = mockk<AccountLister>()
    private val accountTransactionLister = AccountTransactionLister()
    private val testDispatcher = StandardTestDispatcher()
    private val accountId = "test-account-id"
    private val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { accountLister.list() } returns flowOf(Outcome.Success(emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        AccountDetailViewModel(accountId, accountFinder, accountLister, accountTransactionLister, testDispatcher)

    private fun clockAt(instant: String): Clock = object : Clock {
        override fun now(): Instant = Instant.parse(instant)
    }

    @Test
    fun `given a card with a purchase and a payment, when the screen loads, then lists them with running debts`() =
        runTest {
            val visaCard = visaCardWithPurchaseAndPayment().build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value as AccountDetailUiState.CreditCardContent
            assertEquals(TransactionFilter.ALL, state.activeFilter)
            assertEquals(2, state.transactions.size)
            assertTrue(state.transactions[0].transaction is Income)
            assertEquals(Money.of(BigDecimal("400000"), Currency.COP), state.transactions[0].runningBalance)
            assertTrue(state.transactions[1].transaction is Expense)
            assertEquals(Money.of(BigDecimal("700000"), Currency.COP), state.transactions[1].runningBalance)
        }

    @Test
    fun `given a card with movements, when filter changed to INCOME, then shows only payments without debt`() =
        runTest {
            val visaCard = visaCardWithPurchaseAndPayment().build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onFilterChanged(TransactionFilter.INCOME)
            val state = viewModel.uiState.value as AccountDetailUiState.CreditCardContent
            assertEquals(TransactionFilter.INCOME, state.activeFilter)
            assertEquals(1, state.transactions.size)
            assertTrue(state.transactions.single().transaction is Income)
            assertNull(state.transactions.single().runningBalance)
        }

    @Test
    fun `given a card with movements, when filter changed to EXPENSE, then shows only purchases without debt`() =
        runTest {
            val visaCard = visaCardWithPurchaseAndPayment().build()
            every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onFilterChanged(TransactionFilter.EXPENSE)
            val state = viewModel.uiState.value as AccountDetailUiState.CreditCardContent
            assertEquals(TransactionFilter.EXPENSE, state.activeFilter)
            assertEquals(1, state.transactions.size)
            assertTrue(state.transactions.single().transaction is Expense)
            assertNull(state.transactions.single().runningBalance)
        }

    @Test
    fun `given a card with a filter active, when filter reset to ALL, then shows all with running debts`() = runTest {
        val visaCard = visaCardWithPurchaseAndPayment().build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onFilterChanged(TransactionFilter.INCOME)
        viewModel.onFilterChanged(TransactionFilter.ALL)
        val state = viewModel.uiState.value as AccountDetailUiState.CreditCardContent
        assertEquals(TransactionFilter.ALL, state.activeFilter)
        assertEquals(2, state.transactions.size)
        assertEquals(Money.of(BigDecimal("400000"), Currency.COP), state.transactions[0].runningBalance)
        assertEquals(Money.of(BigDecimal("700000"), Currency.COP), state.transactions[1].runningBalance)
    }

    @Test
    fun `given a card filtered by INCOME, when a new payment arrives, then stays filtered and includes it`() =
        runTest {
            val visaCard = visaCardWithPurchaseAndPayment().build()
            val foundCard = MutableStateFlow<Outcome<Account>>(Outcome.Success(visaCard))
            every { accountFinder.find(accountId, criteria) } returns foundCard
            val viewModel = buildViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onFilterChanged(TransactionFilter.INCOME)
            foundCard.value = Outcome.Success(
                visaCardWithPurchaseAndPayment()
                    .withIncome(amount = "50000", date = "2026-09-12", clock = clockAt("2026-09-12T10:00:00Z"))
                    .build()
            )
            testDispatcher.scheduler.advanceUntilIdle()
            val state = viewModel.uiState.value as AccountDetailUiState.CreditCardContent
            assertEquals(TransactionFilter.INCOME, state.activeFilter)
            assertEquals(2, state.transactions.size)
            assertTrue(state.transactions.all { it.transaction is Income && it.runningBalance == null })
            assertEquals(Money.of(BigDecimal("50000"), Currency.COP), state.transactions[0].transaction.amount)
        }

    @Test
    fun `given a card with no movements, when the screen loads, then no movements and the creation debt`() = runTest {
        val visaCard = visaCard().build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as AccountDetailUiState.CreditCardContent
        assertTrue(state.transactions.isEmpty())
        assertEquals(Money.of(BigDecimal("500000"), Currency.COP), state.creditCard.debt)
    }

    @Test
    fun `given a card, when a movement is selected, then emits transaction detail navigation`() = runTest {
        val visaCard = visaCardWithPurchaseAndPayment().build()
        every { accountFinder.find(accountId, criteria) } returns flowOf(Outcome.Success(visaCard))
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTransactionSelected("tx-card-1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.navigationEvent.test {
            assertEquals(AccountDetailNavigationTarget.TransactionDetail("tx-card-1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun visaCardWithPurchaseAndPayment(): AccountBuilder = visaCard()
        .withExpense(
            amount = "200000",
            date = "2026-09-05",
            description = "Mercado",
            clock = clockAt("2026-09-05T10:00:00Z")
        )
        .withIncome(
            amount = "300000",
            date = "2026-09-10",
            description = "Pago desde Ahorros",
            clock = clockAt("2026-09-10T10:00:00Z")
        )

    private fun visaCard(): AccountBuilder = AccountBuilder()
        .id(this.accountId)
        .name("Visa")
        .creditCard(
            creditLimit = Money.of(BigDecimal("3000000"), Currency.COP),
            initialDebt = Money.of(BigDecimal("500000"), Currency.COP)
        )
}
