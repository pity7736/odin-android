package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@Suppress("MagicNumber")
class HomeSummaryLoaderTest {

    private val accountLister = mockk<AccountLister>()
    private val homeSummaryLoader = HomeSummaryLoader(accountLister, RecentTransactionLister())

    private val storageError = object : DomainError {
        override val internalMessage = "Storage error"
        override val externalMessage = "Error interno"
    }

    @Test
    fun `given money accounts in one currency, when loaded, then one balance total sums their balances`() = runTest {
        val savingsAccount = moneyAccount("savings", balance = pesos("1000.00"))
        val cashAccount = moneyAccount("cash", balance = pesos("2000.00"))
        val summary = loadSummary(listOf(savingsAccount, cashAccount))
        assertEquals(listOf(pesos("3000.00")), summary.balanceTotals)
    }

    @Test
    fun `given money accounts in different currencies, when loaded, then one balance total per currency`() = runTest {
        val pesosSavingsAccount = moneyAccount("savings-cop", balance = pesos("1000.00"))
        val pesosCheckingAccount = moneyAccount("checking-cop", balance = pesos("2000.00"))
        val dollarsSavingsAccount = moneyAccount("savings-usd", balance = dollars("500.00"))
        val summary = loadSummary(listOf(pesosSavingsAccount, pesosCheckingAccount, dollarsSavingsAccount))
        assertEquals(listOf(pesos("3000.00"), dollars("500.00")), summary.balanceTotals)
    }

    @Test
    fun `given a money account and a card, when loaded, then the card leaves the balance total unchanged`() = runTest {
        val savingsAccount = moneyAccount("savings", balance = pesos("5000000.00"))
        val visaCard = creditCard("visa", creditLimit = pesos("3000000.00"), debt = pesos("800000.00"))
        val summary = loadSummary(listOf(savingsAccount, visaCard))
        assertEquals(listOf(pesos("5000000.00")), summary.balanceTotals)
    }

    @Test
    fun `given a credit card with debt, when loaded, then the debt total equals the card's current debt`() = runTest {
        val purchase = expense("purchase", "visa", date = "2026-09-10", recordedAt = "2026-09-10T10:00:00Z")
        val visaCard = creditCard("visa", debt = pesos("800000.00"), expenses = listOf(purchase))
        val summary = loadSummary(listOf(visaCard))
        assertEquals(listOf(pesos("800100.00")), summary.debtTotals)
    }

    @Test
    fun `given credit cards in different currencies, when loaded, then one debt total per currency`() = runTest {
        val pesosCard = creditCard("visa-cop", debt = pesos("800000.00"))
        val dollarsCard = creditCard(
            "visa-usd",
            creditLimit = dollars("1000.00"),
            debt = dollars("300.00")
        )
        val summary = loadSummary(listOf(pesosCard, dollarsCard))
        assertEquals(listOf(pesos("800000.00"), dollars("300.00")), summary.debtTotals)
    }

    @Test
    fun `given cards with no debt, when loaded, then the debt total is zero in each card's currency`() = runTest {
        val pesosCard = creditCard("visa-cop", debt = pesos("0"))
        val dollarsCard = creditCard("visa-usd", creditLimit = dollars("1000.00"), debt = dollars("0"))
        val summary = loadSummary(listOf(pesosCard, dollarsCard))
        assertEquals(listOf(pesos("0"), dollars("0")), summary.debtTotals)
    }

    @Test
    fun `given no credit cards, when loaded, then there are no debt totals`() = runTest {
        val savingsAccount = moneyAccount("savings")
        val summary = loadSummary(listOf(savingsAccount))
        assertTrue(summary.debtTotals.isEmpty())
    }

    @Test
    fun `given money in COP and USD and a card in COP, when loaded, then debt is totaled for COP only`() = runTest {
        val pesosAccount = moneyAccount("savings-cop", balance = pesos("1000.00"))
        val dollarsAccount = moneyAccount("savings-usd", balance = dollars("500.00"))
        val pesosCard = creditCard("visa-cop", debt = pesos("800000.00"))
        val summary = loadSummary(listOf(pesosAccount, dollarsAccount, pesosCard))
        assertEquals(listOf(pesos("1000.00"), dollars("500.00")), summary.balanceTotals)
        assertEquals(listOf(pesos("800000.00")), summary.debtTotals)
    }

    @Test
    fun `given money in COP and a card in USD, when loaded, then balance is COP only and debt is USD only`() = runTest {
        val pesosAccount = moneyAccount("savings-cop", balance = pesos("1000.00"))
        val dollarsCard = creditCard("visa-usd", creditLimit = dollars("1000.00"), debt = dollars("300.00"))
        val summary = loadSummary(listOf(pesosAccount, dollarsCard))
        assertEquals(listOf(pesos("1000.00")), summary.balanceTotals)
        assertEquals(listOf(dollars("300.00")), summary.debtTotals)
    }

    @Test
    fun `given only credit cards, when loaded, then there are debt totals and no balance totals`() = runTest {
        val visaCard = creditCard("visa", debt = pesos("500000.00"))
        val mastercardCard = creditCard("mastercard", debt = pesos("300000.00"))
        val summary = loadSummary(listOf(visaCard, mastercardCard))
        assertTrue(summary.balanceTotals.isEmpty())
        assertEquals(listOf(pesos("800000.00")), summary.debtTotals)
    }

    @Test
    fun `given money in several currencies, when loaded, then balances are ordered COP, USD, EUR`() = runTest {
        val eurosAccount = moneyAccount(
            "savings-eur",
            balance = euros("200.00"),
            incomes = listOf(
                income("salary-eur", "savings-eur", date = "2026-09-28", recordedAt = "2026-09-28T10:00:00Z")
            )
        )
        val dollarsAccount = moneyAccount(
            "savings-usd",
            balance = dollars("500.00"),
            incomes = listOf(
                income("salary-usd", "savings-usd", date = "2026-09-20", recordedAt = "2026-09-20T10:00:00Z")
            )
        )
        val pesosAccount = moneyAccount("savings-cop", balance = pesos("1000.00"))
        val summary = loadSummary(listOf(eurosAccount, dollarsAccount, pesosAccount))
        assertEquals(listOf(Currency.COP, Currency.USD, Currency.EUR), summary.balanceTotals.map { it.currency })
    }

    @Test
    fun `given cards in several currencies, when loaded, then debts are ordered COP, USD, EUR`() = runTest {
        val dollarsCard = creditCard(
            "visa-usd",
            creditLimit = dollars("1000.00"),
            debt = dollars("300.00"),
            expenses = listOf(expense("hotel", "visa-usd", date = "2026-09-28", recordedAt = "2026-09-28T10:00:00Z"))
        )
        val eurosCard = creditCard("visa-eur", creditLimit = euros("1000.00"), debt = euros("100.00"))
        val pesosCard = creditCard("visa-cop", debt = pesos("800000.00"))
        val summary = loadSummary(listOf(dollarsCard, eurosCard, pesosCard))
        assertEquals(listOf(Currency.COP, Currency.USD, Currency.EUR), summary.debtTotals.map { it.currency })
    }

    @Test
    fun `given a money account, when loaded, then its entry holds its name, type, and balance`() = runTest {
        val cashAccount = AccountBuilder()
            .id("cash")
            .name("Efectivo")
            .type(AccountType.CASH)
            .initialBalance(pesos("150000.00"))
            .build()
        val summary = loadSummary(listOf(cashAccount))
        assertEquals(
            listOf(
                HomeAccountEntry.MoneyAccountEntry(
                    id = "cash",
                    name = "Efectivo",
                    type = AccountType.CASH,
                    balance = pesos("150000.00")
                )
            ),
            summary.entries
        )
    }

    @Test
    fun `given a credit card, when loaded, then its entry holds its name, debt, and available credit`() = runTest {
        val visaCard = AccountBuilder()
            .id("visa")
            .name("Visa")
            .creditCard(creditLimit = pesos("3000000.00"), initialDebt = pesos("500000.00"))
            .build()
        val summary = loadSummary(listOf(visaCard))
        assertEquals(
            listOf(
                HomeAccountEntry.CreditCardEntry(
                    id = "visa",
                    name = "Visa",
                    debt = pesos("500000.00"),
                    availableCredit = pesos("2500000.00")
                )
            ),
            summary.entries
        )
    }

    @Test
    fun `given two money accounts and two cards, when loaded, then three entries show and more exist`() = runTest {
        val savingsAccount = moneyAccount("savings")
        val cashAccount = moneyAccount("cash")
        val visaCard = creditCard("visa")
        val mastercardCard = creditCard("mastercard")
        val summary = loadSummary(listOf(savingsAccount, cashAccount, visaCard, mastercardCard))
        assertEquals(DISPLAYED_ACCOUNT_LIMIT, summary.entries.size)
        assertTrue(summary.hasMoreEntries)
    }

    @Test
    fun `given three or fewer accounts and cards, when loaded, then more entries do not exist`() = runTest {
        val savingsAccount = moneyAccount("savings")
        val cashAccount = moneyAccount("cash")
        val visaCard = creditCard("visa")
        val summary = loadSummary(listOf(savingsAccount, cashAccount, visaCard))
        assertEquals(3, summary.entries.size)
        assertFalse(summary.hasMoreEntries)
    }

    @Test
    fun `given no accounts and no cards, when loaded, then there are no entries`() = runTest {
        val summary = loadSummary(emptyList())
        assertTrue(summary.entries.isEmpty())
        assertFalse(summary.hasMoreEntries)
    }

    @Test
    fun `given different latest transaction dates, when loaded, then entries follow the latest date`() = runTest {
        val savingsAccount = moneyAccount(
            "savings",
            incomes = listOf(income("savings-income", "savings", "2026-09-10", "2026-09-10T10:00:00Z"))
        )
        val visaCard = creditCard(
            "visa",
            expenses = listOf(expense("visa-purchase", "visa", "2026-09-28", "2026-09-28T10:00:00Z"))
        )
        val cashAccount = moneyAccount(
            "cash",
            incomes = listOf(income("cash-income", "cash", "2026-09-20", "2026-09-20T10:00:00Z"))
        )
        val summary = loadSummary(listOf(savingsAccount, visaCard, cashAccount))
        assertEquals(listOf("visa", "cash", "savings"), summary.entries.map { it.id })
    }

    @Test
    fun `given a backdated transaction recorded today, when loaded, then its date decides the order`() = runTest {
        val savingsAccount = moneyAccount(
            "savings",
            incomes = listOf(income("savings-income", "savings", "2026-09-20", "2026-09-20T10:00:00Z")),
            expenses = listOf(expense("backdated-expense", "savings", "2026-09-01", "2026-09-30T10:00:00Z"))
        )
        val cashAccount = moneyAccount(
            "cash",
            incomes = listOf(income("cash-income", "cash", "2026-09-25", "2026-09-25T10:00:00Z"))
        )
        val summary = loadSummary(listOf(savingsAccount, cashAccount))
        assertEquals(listOf("cash", "savings"), summary.entries.map { it.id })
    }

    @Test
    fun `given latest transactions on the same date, when loaded, then the latest recorded comes first`() = runTest {
        val savingsAccount = moneyAccount(
            "savings",
            incomes = listOf(income("savings-income", "savings", "2026-09-28", "2026-09-28T08:00:00Z"))
        )
        val visaCard = creditCard(
            "visa",
            expenses = listOf(expense("visa-purchase", "visa", "2026-09-28", "2026-09-28T14:00:00Z"))
        )
        val summary = loadSummary(listOf(savingsAccount, visaCard))
        assertEquals(listOf("visa", "savings"), summary.entries.map { it.id })
    }

    @Test
    fun `given accounts without transactions, when loaded, then they come last, newest created first`() = runTest {
        val cashAccount = AccountBuilder()
            .id("cash")
            .createdAt(Instant.parse("2026-09-01T00:00:00Z"))
            .build()
        val visaCard = AccountBuilder()
            .id("visa")
            .creditCard(creditLimit = pesos("3000000.00"), initialDebt = pesos("0"))
            .createdAt(Instant.parse("2026-09-15T00:00:00Z"))
            .build()
        val savingsAccount = AccountBuilder()
            .id("savings")
            .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
            .incomes(listOf(income("savings-income", "savings", "2026-08-10", "2026-08-10T10:00:00Z")))
            .build()
        val summary = loadSummary(listOf(cashAccount, visaCard, savingsAccount))
        assertEquals(listOf("savings", "visa", "cash"), summary.entries.map { it.id })
    }

    @Test
    fun `given a transfer, when loaded, then both accounts move ahead of older accounts`() = runTest {
        val checkingAccount = moneyAccount(
            "checking",
            incomes = listOf(income("checking-income", "checking", "2026-09-20", "2026-09-20T10:00:00Z"))
        )
        val savingsAccount = moneyAccount(
            "savings",
            incomes = listOf(income("savings-income", "savings", "2026-09-05", "2026-09-05T10:00:00Z")),
            expenses = listOf(expense("transfer-out", "savings", "2026-09-28", "2026-09-28T10:00:00Z"))
        )
        val cashAccount = moneyAccount(
            "cash",
            incomes = listOf(income("transfer-in", "cash", "2026-09-28", "2026-09-28T10:00:00Z"))
        )
        val summary = loadSummary(listOf(checkingAccount, savingsAccount, cashAccount))
        assertEquals(setOf("savings", "cash"), summary.entries.take(2).map { it.id }.toSet())
        assertEquals("checking", summary.entries.last().id)
    }

    @Test
    fun `given a card payment, when loaded, then the paying account and the card move ahead of older ones`() = runTest {
        val cashAccount = moneyAccount(
            "cash",
            incomes = listOf(income("cash-income", "cash", "2026-09-20", "2026-09-20T10:00:00Z"))
        )
        val savingsAccount = moneyAccount(
            "savings",
            expenses = listOf(expense("payment-out", "savings", "2026-09-28", "2026-09-28T10:00:00Z"))
        )
        val visaCard = creditCard(
            "visa",
            incomes = listOf(income("payment-in", "visa", "2026-09-28", "2026-09-28T10:00:00Z"))
        )
        val summary = loadSummary(listOf(cashAccount, savingsAccount, visaCard))
        assertEquals(setOf("savings", "visa"), summary.entries.take(2).map { it.id }.toSet())
        assertEquals("cash", summary.entries.last().id)
    }

    @Test
    fun `given a card purchase and payment, when loaded, then both appear in recent transactions`() = runTest {
        val purchase = expense("visa-purchase", "visa", "2026-09-20", "2026-09-20T10:00:00Z")
        val payment = income("payment-in", "visa", "2026-09-28", "2026-09-28T10:00:00Z")
        val paymentFromSavings = expense("payment-out", "savings", "2026-09-28", "2026-09-28T10:00:00Z")
        val visaCard = AccountBuilder()
            .id("visa")
            .name("Visa")
            .creditCard(creditLimit = pesos("3000000.00"), initialDebt = pesos("500000.00"))
            .incomes(listOf(payment))
            .expenses(listOf(purchase))
            .build()
        val savingsAccount = moneyAccount("savings", name = "Ahorros", expenses = listOf(paymentFromSavings))
        val summary = loadSummary(listOf(savingsAccount, visaCard))
        assertTrue(summary.recentTransactions.contains(RecentTransaction(purchase, "Visa")))
        assertTrue(summary.recentTransactions.contains(RecentTransaction(payment, "Visa")))
        assertTrue(summary.recentTransactions.contains(RecentTransaction(paymentFromSavings, "Ahorros")))
    }

    @Test
    fun `given accounts with transactions, when loaded, then recent transactions carry the account name`() = runTest {
        val salary = income("salary", "savings", "2026-09-10", "2026-09-10T10:00:00Z")
        val savingsAccount = moneyAccount("savings", name = "Ahorros", incomes = listOf(salary))
        val summary = loadSummary(listOf(savingsAccount))
        assertEquals(listOf(RecentTransaction(salary, "Ahorros")), summary.recentTransactions)
    }

    @Test
    fun `given accounts but no transactions, when loaded, then there are no recent transactions`() = runTest {
        val summary = loadSummary(listOf(moneyAccount("savings")))
        assertTrue(summary.recentTransactions.isEmpty())
    }

    @Test
    fun `given one money account and one credit card, when loaded, then transfer is available`() = runTest {
        val summary = loadSummary(listOf(moneyAccount("savings"), creditCard("visa")))
        assertTrue(summary.canTransfer)
    }

    @Test
    fun `given two money accounts, when loaded, then transfer is available`() = runTest {
        val summary = loadSummary(listOf(moneyAccount("savings"), moneyAccount("cash")))
        assertTrue(summary.canTransfer)
    }

    @Test
    fun `given exactly one money account, when loaded, then transfer is not available`() = runTest {
        val summary = loadSummary(listOf(moneyAccount("savings")))
        assertFalse(summary.canTransfer)
    }

    @Test
    fun `given two credit cards and no money account, when loaded, then transfer is not available`() = runTest {
        val summary = loadSummary(listOf(creditCard("visa"), creditCard("mastercard")))
        assertFalse(summary.canTransfer)
    }

    @Test
    fun `given a money account, when loading the summary, then income can be recorded`() = runTest {
        val summary = loadSummary(listOf(moneyAccount("savings")))
        assertTrue(summary.canRecordIncome)
    }

    @Test
    fun `given a money account and a credit card, when loading the summary, then income can be recorded`() = runTest {
        val summary = loadSummary(listOf(moneyAccount("savings"), creditCard("visa")))
        assertTrue(summary.canRecordIncome)
    }

    @Test
    fun `given only credit cards, when loading the summary, then income cannot be recorded`() = runTest {
        val summary = loadSummary(listOf(creditCard("visa"), creditCard("mastercard")))
        assertFalse(summary.canRecordIncome)
    }

    @Test
    fun `given the accounts fail to load, when loaded, then the failure is returned`() = runTest {
        every { accountLister.list(fullAccountCriteria) } returns flowOf(Outcome.Failure(storageError))
        val outcome = homeSummaryLoader.load().first()
        assertEquals(Outcome.Failure(storageError), outcome)
    }

    private suspend fun loadSummary(accounts: List<Account>): HomeSummary {
        every { accountLister.list(fullAccountCriteria) } returns flowOf(Outcome.Success(accounts))
        return (homeSummaryLoader.load().first() as Outcome.Success).value
    }

    private fun moneyAccount(
        id: String,
        name: String = "Cuenta $id",
        balance: Money = pesos("100000.00"),
        incomes: List<Income> = emptyList(),
        expenses: List<Expense> = emptyList(),
    ): Account = AccountBuilder()
        .id(id)
        .name(name)
        .initialBalance(balance)
        .incomes(incomes)
        .expenses(expenses)
        .build()

    private fun creditCard(
        id: String,
        creditLimit: Money = pesos("3000000.00"),
        debt: Money = pesos("500000.00"),
        incomes: List<Income> = emptyList(),
        expenses: List<Expense> = emptyList(),
    ): Account = AccountBuilder()
        .id(id)
        .name("Tarjeta $id")
        .creditCard(creditLimit = creditLimit, initialDebt = debt)
        .incomes(incomes)
        .expenses(expenses)
        .build()

    private fun income(id: String, accountId: String, date: String, recordedAt: String): Income = Income.restore(
        id = id,
        accountId = accountId,
        amount = pesos("100.00"),
        date = LocalDate.parse(date),
        categoryId = "cat-1",
        description = "",
        createdAt = Instant.parse(recordedAt)
    )

    private fun expense(id: String, accountId: String, date: String, recordedAt: String): Expense = Expense.restore(
        id = id,
        accountId = accountId,
        amount = pesos("100.00"),
        date = LocalDate.parse(date),
        categoryId = "cat-2",
        description = "",
        createdAt = Instant.parse(recordedAt),
        tagIds = emptyList()
    )

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)

    private fun dollars(amount: String): Money = Money.of(BigDecimal(amount), Currency.USD)

    private fun euros(amount: String): Money = Money.of(BigDecimal(amount), Currency.EUR)

    companion object {
        private val fullAccountCriteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
    }
}
