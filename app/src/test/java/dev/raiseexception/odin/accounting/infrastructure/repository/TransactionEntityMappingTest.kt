package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TransactionEntityMappingTest {

    @Test
    fun `given an income, when mapped to entity and back, then all fields are preserved`() {
        val income = Income.restore(
            id = "inc-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("1500.50"), Currency.COP),
            date = LocalDate.parse("2026-08-15"),
            categoryId = "cat-1",
            description = "Pago mensual",
            createdAt = Instant.parse("2026-08-15T10:00:00Z")
        )
        val entity = income.toEntity()
        assertEquals("INCOME", entity.type)
        val restored = entity.toIncome()
        assertEquals(income.id, restored.id)
        assertEquals(income.accountId, restored.accountId)
        assertEquals(income.amount, restored.amount)
        assertEquals(income.date, restored.date)
        assertEquals(income.categoryId, restored.categoryId)
        assertEquals(income.description, restored.description)
        assertEquals(income.createdAt, restored.createdAt)
    }

    @Test
    fun `given an expense, when mapped to entity and back, then all fields are preserved`() {
        val expense = Expense.restore(
            id = "exp-1",
            accountId = "acc-1",
            amount = Money.of(BigDecimal("200.00"), Currency.USD),
            date = LocalDate.parse("2026-08-20"),
            categoryId = "cat-2",
            description = "Mercado",
            createdAt = Instant.parse("2026-08-20T15:00:00Z")
        )
        val entity = expense.toEntity()
        assertEquals("EXPENSE", entity.type)
        val restored = entity.toExpense()
        assertEquals(expense.id, restored.id)
        assertEquals(expense.accountId, restored.accountId)
        assertEquals(expense.amount, restored.amount)
        assertEquals(expense.date, restored.date)
        assertEquals(expense.categoryId, restored.categoryId)
        assertEquals(expense.description, restored.description)
        assertEquals(expense.createdAt, restored.createdAt)
    }
}
