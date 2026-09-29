package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

@Suppress("MagicNumber")
class AccountTransactionListerTest {

    private val lister = AccountTransactionLister()
    private val initialBalance = Money.of(BigDecimal("1000.00"), Currency.COP)
    private val cardCreditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP)
    private val cardInitialDebt = Money.of(BigDecimal("500000.00"), Currency.COP)

    private fun clockAt(instant: String): Clock = object : Clock {
        override fun now(): Instant = Instant.parse(instant)
    }

    private fun listTransactions(
        account: Account,
        filter: TransactionFilter
    ): List<AccountTransaction> = lister.list(account, filter)

    @Test
    fun `given incomes and expenses, when listing all, then returns all sorted by date descending`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "200.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "100.00", date = "2026-08-27", clock = clockAt("2026-08-27T10:00:00Z"))
            .withIncome(amount = "300.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.ALL)
        assertEquals(3, result.size)
        assertEquals("2026-08-27", result[0].transaction.date.toString())
        assertEquals("2026-08-26", result[1].transaction.date.toString())
        assertEquals("2026-08-25", result[2].transaction.date.toString())
    }

    @Test
    fun `given incomes and expenses, when listing all, then groups same date entries by createdAt descending`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "200.00", date = "2026-08-25", clock = clockAt("2026-08-25T08:00:00Z"))
            .withExpense(amount = "100.00", date = "2026-08-25", clock = clockAt("2026-08-25T14:00:00Z"))
            .withIncome(amount = "50.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.ALL)
        assertEquals(3, result.size)
        assertTrue(result[0].transaction is Expense)
        assertEquals(0, result[0].transaction.amount.amount.compareTo(BigDecimal("100.00")))
        assertTrue(result[1].transaction is Income)
        assertEquals(0, result[1].transaction.amount.amount.compareTo(BigDecimal("50.00")))
        assertTrue(result[2].transaction is Income)
        assertEquals(0, result[2].transaction.amount.amount.compareTo(BigDecimal("200.00")))
    }

    @Test
    fun `given incomes and expenses, when filtering by income, then returns only incomes`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "200.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "100.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .withIncome(amount = "300.00", date = "2026-08-27", clock = clockAt("2026-08-27T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.INCOME)
        assertEquals(2, result.size)
        assertTrue(result.all { it.transaction is Income })
        assertEquals("2026-08-27", result[0].transaction.date.toString())
        assertEquals("2026-08-25", result[1].transaction.date.toString())
    }

    @Test
    fun `given incomes and expenses, when filtering by expense, then returns only expenses`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "200.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "100.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .withExpense(amount = "50.00", date = "2026-08-27", clock = clockAt("2026-08-27T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.EXPENSE)
        assertEquals(2, result.size)
        assertTrue(result.all { it.transaction is Expense })
        assertEquals("2026-08-27", result[0].transaction.date.toString())
        assertEquals("2026-08-26", result[1].transaction.date.toString())
    }

    @Test
    fun `given incomes and expenses, when listing all, then each entry has a running balance`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.ALL)
        assertTrue(result.all { it.runningBalance != null })
    }

    @Test
    fun `given incomes and expenses, when listing all, then first entry running balance equals account balance`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.ALL)
        val expectedBalance = Money.of(BigDecimal("1300.00"), Currency.COP)
        assertEquals(expectedBalance, result[0].runningBalance)
        val expectedSecondBalance = Money.of(BigDecimal("1500.00"), Currency.COP)
        assertEquals(expectedSecondBalance, result[1].runningBalance)
    }

    @Test
    fun `given incomes and expenses, when filtering by income, then running balance is null`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.INCOME)
        result.forEach { assertNull(it.runningBalance) }
    }

    @Test
    fun `given incomes and expenses, when filtering by expense, then running balance is null`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.EXPENSE)
        result.forEach { assertNull(it.runningBalance) }
    }

    @Test
    fun `given no incomes and no expenses, when listing all, then returns empty list`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .build()
        val result = listTransactions(account, TransactionFilter.ALL)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `given only incomes, when filtering by expense, then returns empty list`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withIncome(amount = "500.00", date = "2026-08-25", clock = clockAt("2026-08-25T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.EXPENSE)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `given only expenses, when filtering by income, then returns empty list`() {
        val account = AccountBuilder()
            .initialBalance(initialBalance)
            .withExpense(amount = "200.00", date = "2026-08-26", clock = clockAt("2026-08-26T10:00:00Z"))
            .build()
        val result = listTransactions(account, TransactionFilter.INCOME)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `given a card with a purchase and a later payment, when listing all, then payment first with its debt`() {
        val card = this.visaWithPurchaseAndPayment()
        val result = listTransactions(card, TransactionFilter.ALL)
        assertEquals(2, result.size)
        assertTrue(result[0].transaction is Income)
        assertEquals(Money.of(BigDecimal("400000.00"), Currency.COP), result[0].runningBalance)
        assertTrue(result[1].transaction is Expense)
        assertEquals("Mercado", result[1].transaction.description)
        assertEquals(Money.of(BigDecimal("700000.00"), Currency.COP), result[1].runningBalance)
    }

    @Test
    fun `given a card with purchases and payments, when listing all, then most recent debt equals card debt`() {
        val card = this.visaWithPurchaseAndPayment()
        val result = listTransactions(card, TransactionFilter.ALL)
        assertEquals(card.balance, result[0].runningBalance)
    }

    @Test
    fun `given a card with movements, when walking past the oldest, then reaches the creation debt`() {
        val card = this.visaWithPurchaseAndPayment()
        val result = listTransactions(card, TransactionFilter.ALL)
        val oldest = result.last()
        val debtBeforeOldest = oldest.runningBalance!!.amount.subtract(card.funding.movementEffect(oldest.transaction))
        assertEquals(0, debtBeforeOldest.compareTo(cardInitialDebt.amount))
        assertEquals(card.transactions.size, result.size)
    }

    @Test
    fun `given a card with purchases and payments, when filtering by income, then only payments without debt`() {
        val card = this.visaWithPurchaseAndPayment()
        val result = listTransactions(card, TransactionFilter.INCOME)
        assertEquals(1, result.size)
        assertTrue(result[0].transaction is Income)
        assertNull(result[0].runningBalance)
    }

    @Test
    fun `given a card with purchases and payments, when filtering by expense, then only purchases without debt`() {
        val card = this.visaWithPurchaseAndPayment()
        val result = listTransactions(card, TransactionFilter.EXPENSE)
        assertEquals(1, result.size)
        assertTrue(result[0].transaction is Expense)
        assertNull(result[0].runningBalance)
    }

    @Test
    fun `given a card with no movements, when listing all, then returns empty list`() {
        val card = AccountBuilder()
            .creditCard(cardCreditLimit, cardInitialDebt)
            .build()
        val result = listTransactions(card, TransactionFilter.ALL)
        assertTrue(result.isEmpty())
    }

    private fun visaWithPurchaseAndPayment(): Account = AccountBuilder()
        .name("Visa")
        .creditCard(cardCreditLimit, cardInitialDebt)
        .withExpense(
            amount = "200000.00",
            date = "2026-09-05",
            description = "Mercado",
            clock = clockAt("2026-09-05T10:00:00Z")
        )
        .withIncome(
            amount = "300000.00",
            date = "2026-09-10",
            description = "Pago desde Ahorros",
            clock = clockAt("2026-09-10T10:00:00Z")
        )
        .build()
}
