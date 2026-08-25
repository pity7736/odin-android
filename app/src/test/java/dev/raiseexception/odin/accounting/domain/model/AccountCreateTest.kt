package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private const val MAX_NAME_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 500

class AccountCreateTest {

    @Test
    fun `given all valid fields, when creating an account, then returns success`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = BigDecimal("1500.00"),
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = "Fondo de emergencia"
        )

        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertEquals("Ahorros", account.name)
        assertEquals(0, account.initialBalance.amount.compareTo(BigDecimal("1500.00")))
        assertEquals(Currency.COP, account.currency)
        assertEquals(AccountType.SAVINGS, account.type)
        assertEquals("Fondo de emergencia", account.description)
        assertTrue(account.id.isNotEmpty())
    }

    @Test
    fun `given a zero balance, when creating an account, then returns success with zero balance`() {
        val result = Account.create(
            name = "Efectivo",
            initialBalance = BigDecimal("0"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertEquals(0, account.initialBalance.amount.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `given a name with surrounding spaces, when creating an account, then stores the trimmed name`() {
        val result = Account.create(
            name = "  Ahorros  ",
            initialBalance = BigDecimal("10.00"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertTrue(result is Outcome.Success)
        assertEquals("Ahorros", (result as Outcome.Success).value.name)
    }

    @Test
    fun `given a blank name, when creating an account, then returns name required error`() {
        val result = Account.create(
            name = "   ",
            initialBalance = BigDecimal("10.00"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        val error = failureInvalidInput(result)
        assertEquals("El nombre es obligatorio.", error.nameError)
    }

    @Test
    fun `given a name longer than 200 characters, when creating an account, then returns name too long error`() {
        val result = Account.create(
            name = "a".repeat(MAX_NAME_LENGTH + 1),
            initialBalance = BigDecimal("10.00"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        val error = failureInvalidInput(result)
        assertEquals("El nombre no puede superar los 200 caracteres.", error.nameError)
    }

    @Test
    fun `given a negative balance, when creating an account, then returns negative balance error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = BigDecimal("-1.00"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        val error = failureInvalidInput(result)
        assertEquals("El saldo inicial no puede ser negativo.", error.balanceError)
    }

    @Test
    fun `given a balance with more than two decimals, when creating an account, then returns decimals error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = BigDecimal("10.255"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        val error = failureInvalidInput(result)
        assertEquals("El saldo inicial admite máximo 2 decimales.", error.balanceError)
    }

    @Test
    fun `given a description longer than 500 characters, when creating, then returns description too long error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = BigDecimal("10.00"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = "a".repeat(MAX_DESCRIPTION_LENGTH + 1)
        )

        val error = failureInvalidInput(result)
        assertEquals("La descripción no puede superar los 500 caracteres.", error.descriptionError)
    }

    @Test
    fun `given a blank description, when creating an account, then stores an empty description`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = BigDecimal("10.00"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = "    "
        )

        assertTrue(result is Outcome.Success)
        assertEquals("", (result as Outcome.Success).value.description)
    }

    @Test
    fun `given a blank name and a negative balance, when creating an account, then returns both errors at once`() {
        val result = Account.create(
            name = "",
            initialBalance = BigDecimal("-1.00"),
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        val error = failureInvalidInput(result)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertEquals("El saldo inicial no puede ser negativo.", error.balanceError)
        assertNull(error.descriptionError)
    }

    private fun failureInvalidInput(result: Outcome<Account>): AccountCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is AccountCreationError.InvalidInput)
        return error as AccountCreationError.InvalidInput
    }
}
