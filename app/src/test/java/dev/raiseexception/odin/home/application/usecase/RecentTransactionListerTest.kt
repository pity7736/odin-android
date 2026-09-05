package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RecentTransactionListerTest {

    private val recentTransactionLister = RecentTransactionLister()

    @Test
    fun `given accounts with transactions, when list, then returns transactions sorted by date descending`() {
        val olderIncome = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("100.00"), Currency.COP),
            date = LocalDate.parse("2026-01-01"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-01-01T10:00:00Z")
        )
        val newerExpense = Expense.restore(
            id = "exp-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("50.00"), Currency.COP),
            date = LocalDate.parse("2026-01-15"),
            categoryId = "cat-2",
            description = "",
            createdAt = Instant.parse("2026-01-15T10:00:00Z")
        )
        val account = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .incomes(listOf(olderIncome))
            .expenses(listOf(newerExpense))
            .build()
        val result = recentTransactionLister.list(listOf(account))
        assertEquals(2, result.size)
        assertEquals("exp-1", result[0].transaction.id)
        assertEquals("inc-1", result[1].transaction.id)
    }

    @Test
    fun `given accounts with more than limit transactions, when list, then returns at most limit`() {
        @Suppress("MagicNumber")
        val incomes = (1..6).map { index ->
            Income.restore(
                id = "inc-$index",
                accountId = "acc-1",
                amount = Money.of(BigDecimal("100.00"), Currency.COP),
                date = LocalDate.parse("2026-01-0$index"),
                categoryId = "cat-1",
                description = "",
                createdAt = Instant.parse("2026-01-0${index}T10:00:00Z")
            )
        }
        val account = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .incomes(incomes)
            .build()
        val result = recentTransactionLister.list(listOf(account))
        assertEquals(TRANSACTION_LIMIT, result.size)
    }

    @Test
    fun `given accounts with no transactions, when list, then returns empty list`() {
        val account = AccountBuilder().id("acc-1").name("Ahorros").build()
        val result = recentTransactionLister.list(listOf(account))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `given multiple accounts with transactions, when list, then each result includes the account name`() {
        val income = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("100.00"), Currency.COP),
            date = LocalDate.parse("2026-01-01"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-01-01T10:00:00Z")
        )
        val expense = Expense.restore(
            id = "exp-1",
            accountId = "acc-2",
            amount = Money.of(BigDecimal("50.00"), Currency.USD),
            date = LocalDate.parse("2026-01-02"),
            categoryId = "cat-2",
            description = "",
            createdAt = Instant.parse("2026-01-02T10:00:00Z")
        )
        val savingsAccount = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .incomes(listOf(income))
            .build()
        val checkingAccount = AccountBuilder()
            .id("acc-2")
            .name("Corriente")
            .initialBalance(Money.of(BigDecimal("500.00"), Currency.USD))
            .expenses(listOf(expense))
            .build()
        val result = recentTransactionLister.list(listOf(savingsAccount, checkingAccount))
        assertEquals(2, result.size)
        assertEquals("Corriente", result[0].accountName)
        assertEquals("Ahorros", result[1].accountName)
    }

    @Test
    fun `given transactions with the same date, when list, then sorts by createdAt descending`() {
        val earlierIncome = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("100.00"), Currency.COP),
            date = LocalDate.parse("2026-01-01"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-01-01T08:00:00Z")
        )
        val laterExpense = Expense.restore(
            id = "exp-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("50.00"), Currency.COP),
            date = LocalDate.parse("2026-01-01"),
            categoryId = "cat-2",
            description = "",
            createdAt = Instant.parse("2026-01-01T14:00:00Z")
        )
        val account = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .incomes(listOf(earlierIncome))
            .expenses(listOf(laterExpense))
            .build()
        val result = recentTransactionLister.list(listOf(account))
        assertEquals(2, result.size)
        assertEquals("exp-1", result[0].transaction.id)
        assertEquals("inc-1", result[1].transaction.id)
    }
}
