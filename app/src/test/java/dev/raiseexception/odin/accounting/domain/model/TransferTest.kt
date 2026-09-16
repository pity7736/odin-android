package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TransferTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }

    private val sourceAccount = AccountBuilder()
        .id("src-1")
        .name("Ahorros")
        .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
        .build()

    private val destinationAccount = AccountBuilder()
        .id("dst-1")
        .name("Efectivo")
        .initialBalance(Money.of(BigDecimal("500.00"), Currency.COP))
        .build()

    @Test
    fun `given valid accounts with same currency and sufficient funds, when creating transfer, then succeeds`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = "200.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        assertTrue(result is Outcome.Success)
        val transfer = (result as Outcome.Success).value
        assertEquals(Money.of(BigDecimal("200.00"), Currency.COP), transfer.expense.amount)
        assertEquals(Money.of(BigDecimal("200.00"), Currency.COP), transfer.income.amount)
        assertEquals(sourceAccount.id, transfer.expense.accountId)
        assertEquals(destinationAccount.id, transfer.income.accountId)
    }

    @Test
    fun `given successful transfer, when checking expense description, then contains destination name`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = "200.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        val transfer = (result as Outcome.Success).value
        assertEquals("Transferencia a Efectivo", transfer.expense.description)
    }

    @Test
    fun `given successful transfer, when checking income description, then it is "Transferencia desde source name"`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = "200.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        val transfer = (result as Outcome.Success).value
        assertEquals("Transferencia desde Ahorros", transfer.income.description)
    }

    @Test
    fun `given same source and destination, when creating transfer, then returns source account error`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = sourceAccount,
            amount = "200.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as TransferCreationError.InvalidInput
        assertNotNull(error.sourceAccountError)
    }

    @Test
    fun `given different currencies, when creating transfer, then returns destination account error`() {
        val usdAccount = AccountBuilder()
            .id("usd-1")
            .name("Dólares")
            .initialBalance(Money.of(BigDecimal("500.00"), Currency.USD))
            .build()
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = usdAccount,
            amount = "200.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as TransferCreationError.InvalidInput
        assertNotNull(error.destinationAccountError)
    }

    @Test
    fun `given amount zero, when creating transfer, then returns failure with amount error`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = "0",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as TransferCreationError.InvalidInput
        assertNotNull(error.amountError)
    }

    @Test
    fun `given negative amount, when creating transfer, then returns failure with amount error`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = "-100",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as TransferCreationError.InvalidInput
        assertNotNull(error.amountError)
    }

    @Test
    fun `given insufficient funds in source, when creating transfer, then returns failure with amount error`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = "1500.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            clock = fixedClock
        )
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as TransferCreationError.InvalidInput
        assertNotNull(error.amountError)
    }

    @Test
    fun `given a restored transfer, when accessing properties, then returns the stored values`() {
        val expense = Expense.restore(
            id = "exp-1",
            accountId = "src-1",
            amount = Money.of(BigDecimal("200.00"), Currency.COP),
            date = kotlinx.datetime.LocalDate.parse("2026-08-29"),
            categoryId = "cat-transfer",
            description = "Transferencia a Efectivo",
            createdAt = fixedInstant
        )
        val income = Income.restore(
            id = "inc-1",
            accountId = "dst-1",
            amount = Money.of(BigDecimal("200.00"), Currency.COP),
            date = kotlinx.datetime.LocalDate.parse("2026-08-29"),
            categoryId = "cat-transfer",
            description = "Transferencia desde Ahorros",
            createdAt = fixedInstant
        )
        val transfer = Transfer.restore(
            id = "transfer-1",
            expense = expense,
            income = income,
            createdAt = fixedInstant
        )
        assertEquals("transfer-1", transfer.id)
        assertEquals(expense, transfer.expense)
        assertEquals(income, transfer.income)
        assertEquals(fixedInstant, transfer.createdAt)
    }
}
