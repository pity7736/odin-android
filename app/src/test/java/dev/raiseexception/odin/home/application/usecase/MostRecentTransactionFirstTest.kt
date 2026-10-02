package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class MostRecentTransactionFirstTest {

    @Test
    fun `given transactions on different dates, when sorted, then the latest date comes first`() {
        val olderIncome = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("100.00"), Currency.COP),
            date = LocalDate.parse("2026-09-10"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-09-28T10:00:00Z")
        )
        val newerExpense = Expense.restore(
            id = "exp-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("50.00"), Currency.COP),
            date = LocalDate.parse("2026-09-20"),
            categoryId = "cat-2",
            description = "",
            createdAt = Instant.parse("2026-09-20T10:00:00Z"),
            tagIds = emptyList()
        )
        val sortedTransactions = listOf(olderIncome, newerExpense).sortedWith(mostRecentTransactionFirst)
        assertEquals(listOf("exp-1", "inc-1"), sortedTransactions.map { it.id })
    }

    @Test
    fun `given transactions on the same date, when sorted, then the one recorded most recently comes first`() {
        val earlierRecordedIncome = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("100.00"), Currency.COP),
            date = LocalDate.parse("2026-09-28"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-09-28T08:00:00Z")
        )
        val laterRecordedExpense = Expense.restore(
            id = "exp-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("50.00"), Currency.COP),
            date = LocalDate.parse("2026-09-28"),
            categoryId = "cat-2",
            description = "",
            createdAt = Instant.parse("2026-09-28T14:00:00Z"),
            tagIds = emptyList()
        )
        val sortedTransactions = listOf(earlierRecordedIncome, laterRecordedExpense)
            .sortedWith(mostRecentTransactionFirst)
        assertEquals(listOf("exp-1", "inc-1"), sortedTransactions.map { it.id })
    }
}
