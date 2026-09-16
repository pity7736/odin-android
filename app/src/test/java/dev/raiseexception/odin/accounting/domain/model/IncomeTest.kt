package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class IncomeTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val today = fixedInstant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    private lateinit var account: Account

    @Before
    fun setUp() {
        account = AccountBuilder()
            .id("acc-1")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()
    }

    @Test
    fun `given a valid income, when created via account, then income has correct fields`() {
        val result = account.createIncome(
            amount = "500.00",
            date = today.toString(),
            categoryId = "cat-1",
            description = "Salario",
            clock = fixedClock
        )

        assertTrue(result is Outcome.Success)
        val income = (result as Outcome.Success).value
        assertEquals("acc-1", income.accountId)
        assertEquals(0, income.amount.amount.compareTo(BigDecimal("500.00")))
        assertEquals(today, income.date)
        assertEquals("cat-1", income.categoryId)
        assertEquals("Salario", income.description)
        assertEquals(fixedInstant, income.createdAt)
        assertTrue(income.id.isNotEmpty())
    }

    @Test
    fun `given a zero amount, when account creates income, then returns amount error`() {
        val result = account.createIncome(
            amount = "0",
            date = today.toString(),
            categoryId = "cat-1",
            description = "",
            clock = fixedClock
        )

        val error = assertInvalidInput(result)
        assertNotNull(error.amountError)
        assertNull(error.dateError)
        assertNull(error.categoryError)
    }

    @Test
    fun `given a negative amount, when account creates income, then returns amount error`() {
        val result = account.createIncome(
            amount = "-100.00",
            date = today.toString(),
            categoryId = "cat-1",
            description = "",
            clock = fixedClock
        )

        val error = assertInvalidInput(result)
        assertNotNull(error.amountError)
    }

    @Test
    fun `given a future date, when account creates income, then returns date error`() {
        val result = account.createIncome(
            amount = "500.00",
            date = "2099-01-01",
            categoryId = "cat-1",
            description = "",
            clock = fixedClock
        )

        val error = assertInvalidInput(result)
        assertNotNull(error.dateError)
        assertNull(error.amountError)
        assertNull(error.categoryError)
    }

    @Test
    fun `given a blank date, when account creates income, then returns date error`() {
        val result = account.createIncome(
            amount = "500.00",
            date = "",
            categoryId = "cat-1",
            description = "",
            clock = fixedClock
        )

        val error = assertInvalidInput(result)
        assertNotNull(error.dateError)
        assertNull(error.amountError)
        assertNull(error.categoryError)
    }

    @Test
    fun `given an invalid date format, when account creates income, then returns date error`() {
        val result = account.createIncome(
            amount = "500.00",
            date = "2026-09-91",
            categoryId = "cat-1",
            description = "",
            clock = fixedClock
        )

        val error = assertInvalidInput(result)
        assertNotNull(error.dateError)
        assertNull(error.amountError)
        assertNull(error.categoryError)
    }

    @Test
    fun `given a missing amount, when account creates income, then returns amount error`() {
        val result = account.createIncome(
            amount = "",
            date = today.toString(),
            categoryId = "cat-1",
            description = "",
            clock = fixedClock
        )

        val error = assertInvalidInput(result)
        assertNotNull(error.amountError)
    }

    @Test
    fun `given a missing category, when account creates income, then returns category error`() {
        val result = account.createIncome(
            amount = "500.00",
            date = today.toString(),
            categoryId = "",
            description = "",
            clock = fixedClock
        )

        val error = assertInvalidInput(result)
        assertNotNull(error.categoryError)
        assertNull(error.amountError)
        assertNull(error.dateError)
    }

    @Test
    fun `given an account created on march 1, when creating income with february 28, then returns date error`() {
        val marchFirst = Instant.parse("2026-03-01T12:00:00Z")
        val marchClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-03-15T12:00:00Z")
        }
        val accountCreatedInMarch = AccountBuilder()
            .id("acc-1")
            .createdAt(marchFirst)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()

        val result = accountCreatedInMarch.createIncome(
            amount = "500.00",
            date = "2026-02-28",
            categoryId = "cat-1",
            description = "",
            clock = marchClock
        )

        val error = assertInvalidInput(result)
        assertEquals("La fecha no puede ser anterior a la fecha de creación de la cuenta.", error.dateError)
        assertNull(error.amountError)
        assertNull(error.categoryError)
    }

    @Test
    fun `given an account created on march 1, when creating income with march 1, then succeeds`() {
        val marchFirst = Instant.parse("2026-03-01T12:00:00Z")
        val marchClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-03-15T12:00:00Z")
        }
        val accountCreatedInMarch = AccountBuilder()
            .id("acc-1")
            .createdAt(marchFirst)
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()

        val result = accountCreatedInMarch.createIncome(
            amount = "500.00",
            date = "2026-03-01",
            categoryId = "cat-1",
            description = "",
            clock = marchClock
        )

        assertTrue(result is Outcome.Success)
    }

    private fun assertInvalidInput(result: Outcome<Income>): IncomeCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeCreationError.InvalidInput)
        return error as IncomeCreationError.InvalidInput
    }
}
