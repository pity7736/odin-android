package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.ExpenseUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private const val MAX_NAME_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 500

class AccountCreateTest {

    @Test
    fun `given all valid fields, when creating an account, then returns success`() {
        val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")
        val fakeClock = object : Clock {
            override fun now(): Instant = fixedInstant
        }

        val result = Account.create(
            name = "Ahorros",
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = "Fondo de emergencia",
            clock = fakeClock
        )

        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        val funds = account.funding as AccountFunding.Funds
        assertEquals("Ahorros", account.name)
        assertEquals(0, funds.initialBalance.amount.compareTo(BigDecimal("1500.00")))
        assertEquals(Currency.COP, account.currency)
        assertEquals(AccountType.SAVINGS, account.type)
        assertEquals("Fondo de emergencia", account.description)
        assertTrue(account.id.isNotEmpty())
        assertEquals(fixedInstant, account.createdAt)
    }

    @Test
    fun `given a zero balance, when creating an account, then returns success with zero balance`() {
        val result = Account.create(
            name = "Efectivo",
            initialBalance = "0",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        assertEquals(0, (account.funding as AccountFunding.Funds).initialBalance.amount.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `given a name with surrounding spaces, when creating an account, then stores the trimmed name`() {
        val result = Account.create(
            name = "  Ahorros  ",
            initialBalance = "10.00",
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
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El nombre es obligatorio.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a name longer than 200 characters, when creating an account, then returns name too long error`() {
        val result = Account.create(
            name = "a".repeat(MAX_NAME_LENGTH + 1),
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El nombre no puede superar los 200 caracteres.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a blank balance, when creating an account, then returns balance required error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El saldo inicial es obligatorio.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a non-numeric balance, when creating an account, then returns invalid number error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "abc",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El saldo inicial no es un número válido.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a negative balance, when creating an account, then returns negative balance error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "-1.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El saldo inicial no puede ser negativo.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a balance with more than two decimals, when creating an account, then returns decimals error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.255",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El saldo inicial admite máximo 2 decimales.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given no currency, when creating an account, then returns currency required error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = null,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("La moneda es obligatoria.", failureInvalidInput(result).currencyError)
    }

    @Test
    fun `given no type, when creating an account, then returns type required error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = null,
            description = ""
        )

        assertEquals("El tipo de cuenta es obligatorio.", failureInvalidInput(result).typeError)
    }

    @Test
    fun `given a description longer than 500 characters, when creating, then returns description too long error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = "a".repeat(MAX_DESCRIPTION_LENGTH + 1)
        )

        assertEquals(
            "La descripción no puede superar los 500 caracteres.",
            failureInvalidInput(result).descriptionError
        )
    }

    @Test
    fun `given a blank description, when creating an account, then stores an empty description`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = "    "
        )

        assertTrue(result is Outcome.Success)
        assertEquals("", (result as Outcome.Success).value.description)
    }

    @Test
    fun `given an empty form, when creating an account, then returns every field error at once`() {
        val result = Account.create(
            name = "",
            initialBalance = "",
            currency = null,
            type = null,
            description = ""
        )

        val error = failureInvalidInput(result)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertEquals("El saldo inicial es obligatorio.", error.balanceError)
        assertEquals("La moneda es obligatoria.", error.currencyError)
        assertEquals("El tipo de cuenta es obligatorio.", error.typeError)
        assertNull(error.descriptionError)
    }

    private fun failureInvalidInput(result: Outcome<Account>): AccountCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is AccountCreationError.InvalidInput)
        return error as AccountCreationError.InvalidInput
    }
}

class AccountBalanceTest {

    @Test
    fun `given an account with no incomes, when computing balance, then returns initial balance`() {
        val account = AccountBuilder()
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()

        assertEquals(Money.of(BigDecimal("1000.00"), Currency.COP), account.balance)
    }

    @Test
    fun `given an account with incomes, when computing balance, then returns initial balance plus sum of incomes`() {
        val account = AccountBuilder()
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "300.00", date = "2026-08-28")
            .withIncome(amount = "200.00", date = "2026-08-28")
            .build()

        assertEquals(Money.of(BigDecimal("1500.00"), Currency.COP), account.balance)
    }

    @Test
    fun `given an account, when creating an income, then balance reflects it immediately`() {
        val account = AccountBuilder()
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .build()

        account.createIncome(
            amount = "500.00",
            date = "2026-08-28",
            categoryId = "cat-1",
            description = ""
        )

        assertEquals(Money.of(BigDecimal("1500.00"), Currency.COP), account.balance)
    }

    @Test
    fun `given an account with incomes and expenses, when computing balance, then returns balance with both`() {
        val account = AccountBuilder()
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withIncome(amount = "500.00", date = "2026-08-28")
            .withExpense(amount = "200.00", date = "2026-08-28")
            .build()

        assertEquals(Money.of(BigDecimal("1300.00"), Currency.COP), account.balance)
    }

    @Test
    fun `given an account with expenses only, when computing balance, then returns initial balance minus expenses`() {
        val account = AccountBuilder()
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withExpense(amount = "300.00", date = "2026-08-28")
            .withExpense(amount = "200.00", date = "2026-08-28")
            .build()

        assertEquals(Money.of(BigDecimal("500.00"), Currency.COP), account.balance)
    }
}

class AccountEditTest {

    @Test
    fun `given valid new values, when edit, then returns account preserving id createdAt and movements`() {
        val original = AccountBuilder()
            .id("acc-1")
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .type(AccountType.SAVINGS)
            .description("Fondo")
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .withIncome(amount = "500.00", date = "2026-01-01")
            .build()

        val result = original.edit(
            name = "Corriente",
            initialBalance = "2000.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = "Gastos diarios"
        )

        assertTrue(result is Outcome.Success)
        val edited = (result as Outcome.Success).value
        assertEquals("Corriente", edited.name)
        assertEquals(0, (edited.funding as AccountFunding.Funds).initialBalance.amount.compareTo(BigDecimal("2000.00")))
        assertEquals(Currency.USD, edited.currency)
        assertEquals(AccountType.CASH, edited.type)
        assertEquals("Gastos diarios", edited.description)
        assertEquals("acc-1", edited.id)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), edited.createdAt)
        assertEquals(1, edited.incomes.size)
    }

    @Test
    fun `given a blank name, when edit, then returns name required error`() {
        val result = AccountBuilder().build().edit(
            name = "   ",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El nombre es obligatorio.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a name longer than 200 characters, when edit, then returns name too long error`() {
        val result = AccountBuilder().build().edit(
            name = "a".repeat(MAX_NAME_LENGTH + 1),
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El nombre no puede superar los 200 caracteres.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a blank balance, when edit, then returns balance required error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El saldo inicial es obligatorio.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a negative balance, when edit, then returns negative balance error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "-1.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El saldo inicial no puede ser negativo.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a balance with more than two decimals, when edit, then returns decimals error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.255",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("El saldo inicial admite máximo 2 decimales.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given no currency, when edit, then returns currency required error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = null,
            type = AccountType.CASH,
            description = ""
        )

        assertEquals("La moneda es obligatoria.", failureInvalidInput(result).currencyError)
    }

    @Test
    fun `given no type, when edit, then returns type required error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = null,
            description = ""
        )

        assertEquals("El tipo de cuenta es obligatorio.", failureInvalidInput(result).typeError)
    }

    @Test
    fun `given a description longer than 500 characters, when edit, then returns description too long error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = "a".repeat(MAX_DESCRIPTION_LENGTH + 1)
        )

        assertEquals(
            "La descripción no puede superar los 500 caracteres.",
            failureInvalidInput(result).descriptionError
        )
    }

    @Test
    fun `given several invalid fields, when edit, then InvalidInput carries every field error at once`() {
        val result = AccountBuilder().build().edit(
            name = "",
            initialBalance = "",
            currency = null,
            type = null,
            description = ""
        )

        val error = failureInvalidInput(result)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertEquals("El saldo inicial es obligatorio.", error.balanceError)
        assertEquals("La moneda es obligatoria.", error.currencyError)
        assertEquals("El tipo de cuenta es obligatorio.", error.typeError)
        assertNull(error.descriptionError)
    }

    @Test
    fun `given a description of only blank spaces, when edit, then description is empty`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            type = AccountType.CASH,
            description = "    "
        )

        assertTrue(result is Outcome.Success)
        assertEquals("", (result as Outcome.Success).value.description)
    }

    private fun failureInvalidInput(result: Outcome<Account>): AccountUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is AccountUpdateError.InvalidInput)
        return error as AccountUpdateError.InvalidInput
    }
}

class AccountHasTransactionsTest {

    @Test
    fun `given account with no movements, when hasTransactions, then false`() {
        val account = AccountBuilder().build()

        assertFalse(account.hasTransactions())
    }

    @Test
    fun `given account with an income, when hasTransactions, then true`() {
        val account = AccountBuilder()
            .withIncome(amount = "500.00", date = "2026-01-01")
            .build()

        assertTrue(account.hasTransactions())
    }

    @Test
    fun `given account with an expense, when hasTransactions, then true`() {
        val account = AccountBuilder()
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .withExpense(amount = "200.00", date = "2026-01-01")
            .build()

        assertTrue(account.hasTransactions())
    }
}

class AccountRestoreTest {

    @Test
    fun `given a stored account record, when restoring, then all fields match exactly`() {
        val knownInstant = Instant.parse("2026-01-15T12:00:00Z")
        val initialBalance = Money.of(BigDecimal("2500.50"), Currency.USD)

        val account = Account.restore(
            id = "test-id-123",
            name = "Cuenta de Ahorros",
            funding = AccountFunding.Funds(initialBalance),
            type = AccountType.SAVINGS,
            description = "Mi cuenta principal",
            createdAt = knownInstant
        )

        assertEquals("test-id-123", account.id)
        assertEquals("Cuenta de Ahorros", account.name)
        assertEquals(initialBalance, (account.funding as AccountFunding.Funds).initialBalance)
        assertEquals(AccountType.SAVINGS, account.type)
        assertEquals("Mi cuenta principal", account.description)
        assertEquals(knownInstant, account.createdAt)
    }
}

class AccountCreateCreditCardTest {

    @Test
    fun `given valid fields with a debt, when creating a credit card, then returns success storing limit and debt`() {
        val fixedInstant = Instant.parse("2026-01-01T00:00:00Z")
        val fakeClock = object : Clock {
            override fun now(): Instant = fixedInstant
        }

        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "3000000",
            existingDebt = "500000",
            clock = fakeClock
        )

        assertTrue(result is Outcome.Success)
        val account = (result as Outcome.Success).value
        val credit = account.funding as AccountFunding.Credit
        assertEquals("Visa", account.name)
        assertEquals(AccountType.CREDIT_CARD, account.type)
        assertEquals(Money.of(BigDecimal("3000000"), Currency.COP), credit.creditLimit)
        assertEquals(Money.of(BigDecimal("500000"), Currency.COP), credit.debt)
        assertEquals(Currency.COP, account.currency)
        assertEquals(fixedInstant, account.createdAt)
    }

    @Test
    fun `given a blank existing debt, when creating a credit card, then debt defaults to zero`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "3000000",
            existingDebt = ""
        )

        assertTrue(result is Outcome.Success)
        val credit = (result as Outcome.Success).value.funding as AccountFunding.Credit
        assertEquals(0, credit.debt.amount.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `given a missing credit limit, when creating a credit card, then returns credit limit required error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "",
            existingDebt = "0"
        )

        assertEquals("El cupo es obligatorio.", failureInvalidInput(result).creditLimitError)
    }

    @Test
    fun `given a zero credit limit, when creating a credit card, then returns greater than zero error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "0",
            existingDebt = "0"
        )

        assertEquals("El cupo debe ser mayor que cero.", failureInvalidInput(result).creditLimitError)
    }

    @Test
    fun `given a negative credit limit, when creating a credit card, then returns greater than zero error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "-100",
            existingDebt = "0"
        )

        assertEquals("El cupo debe ser mayor que cero.", failureInvalidInput(result).creditLimitError)
    }

    @Test
    fun `given a credit limit with more than two decimals, when creating a credit card, then returns decimals error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "1000.255",
            existingDebt = "0"
        )

        assertEquals("El cupo admite máximo 2 decimales.", failureInvalidInput(result).creditLimitError)
    }

    @Test
    fun `given a negative existing debt, when creating a credit card, then returns debt cannot be negative error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "3000000",
            existingDebt = "-1"
        )

        assertEquals("La deuda actual no puede ser negativa.", failureInvalidInput(result).debtError)
    }

    @Test
    fun `given a debt with too many decimals, when creating a credit card, then returns decimals error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "3000000",
            existingDebt = "100.255"
        )

        assertEquals("La deuda actual admite máximo 2 decimales.", failureInvalidInput(result).debtError)
    }

    @Test
    fun `given a debt over the credit limit, when creating a credit card, then returns exceeds error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "1000000",
            existingDebt = "1500000"
        )

        assertEquals("La deuda actual no puede superar el cupo.", failureInvalidInput(result).debtError)
    }

    @Test
    fun `given an existing debt equal to the credit limit, when creating a credit card, then returns success`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "1000000",
            existingDebt = "1000000"
        )

        assertTrue(result is Outcome.Success)
        val credit = (result as Outcome.Success).value.funding as AccountFunding.Credit
        assertEquals(Money.of(BigDecimal("1000000"), Currency.COP), credit.debt)
    }

    @Test
    fun `given no currency, when creating a credit card, then returns currency required error`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = null,
            description = "",
            creditLimit = "3000000",
            existingDebt = "0"
        )

        assertEquals("La moneda es obligatoria.", failureInvalidInput(result).currencyError)
    }

    @Test
    fun `given several invalid fields, when creating a credit card, then InvalidInput carries every field error`() {
        val result = Account.createCreditCard(
            name = "",
            currency = Currency.COP,
            description = "",
            creditLimit = "0",
            existingDebt = "0"
        )

        val error = failureInvalidInput(result)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertEquals("El cupo debe ser mayor que cero.", error.creditLimitError)
        assertNull(error.balanceError)
        assertNull(error.typeError)
    }

    private fun failureInvalidInput(result: Outcome<Account>): AccountCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is AccountCreationError.InvalidInput)
        return error as AccountCreationError.InvalidInput
    }
}

class AccountEditExpenseTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val expenseCreatedAt = Instant.parse("2026-03-10T12:00:00Z")
    private val expenseClock = object : Clock {
        override fun now(): Instant = expenseCreatedAt
    }

    @Test
    fun `given an expense, when editing every field validly, then returns it keeping id account and createdAt`() {
        val account = this.accountWithExpense(description = "Mercado")
        val original = account.expenses.first()

        val result = account.editExpense(
            expenseId = original.id,
            amount = "45000",
            date = "2026-04-02",
            categoryId = "cat-2",
            description = "  Restaurante  ",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        val edited = (result as Outcome.Success).value
        assertEquals(original.id, edited.id)
        assertEquals("acc-1", edited.accountId)
        assertEquals(this.expenseCreatedAt, edited.createdAt)
        assertEquals(Money.of(BigDecimal("45000"), Currency.COP), edited.amount)
        assertEquals(LocalDate.parse("2026-04-02"), edited.date)
        assertEquals("cat-2", edited.categoryId)
        assertEquals("Restaurante", edited.description)
    }

    @Test
    fun `given an expense, when editing it, then the account holds the edited expense and the balance reflects it`() {
        val account = this.accountWithExpense()
        val original = account.expenses.first()

        account.editExpense(
            expenseId = original.id,
            amount = "20000",
            date = "2026-03-10",
            categoryId = "cat-1",
            description = "",
            clock = this.fixedClock
        )

        assertEquals(1, account.expenses.size)
        assertEquals(original.id, account.expenses.first().id)
        assertEquals(Money.of(BigDecimal("20000"), Currency.COP), account.expenses.first().amount)
        assertEquals(Money.of(BigDecimal("80000"), Currency.COP), account.balance)
    }

    @Test
    fun `given expense 30000 and balance 70000, when editing amount to 90000, then succeeds and balance is 10000`() {
        val account = this.accountWithExpense()

        val result = this.editAmount(account, "90000")

        assertTrue(result is Outcome.Success)
        assertEquals(Money.of(BigDecimal("10000"), Currency.COP), account.balance)
    }

    @Test
    fun `given expense 30000 and balance 70000, when editing amount to 100000, then succeeds and balance is 0`() {
        val account = this.accountWithExpense()

        val result = this.editAmount(account, "100000")

        assertTrue(result is Outcome.Success)
        assertEquals(Money.of(BigDecimal.ZERO, Currency.COP), account.balance)
    }

    @Test
    fun `given expense 30000 and balance 70000, when editing amount to 100001, then fails with exceeds error`() {
        val account = this.accountWithExpense()

        val result = this.editAmount(account, "100001")

        val error = this.failureInvalidInput(result)
        assertEquals("El monto supera el saldo disponible.", error.amountError)
        assertNull(error.dateError)
        assertNull(error.categoryError)
        assertEquals(Money.of(BigDecimal("70000"), Currency.COP), account.balance)
    }

    @Test
    fun `given an expense of 30000, when editing the amount to 10000, then succeeds and balance is 90000`() {
        val account = this.accountWithExpense()

        val result = this.editAmount(account, "10000")

        assertTrue(result is Outcome.Success)
        assertEquals(Money.of(BigDecimal("90000"), Currency.COP), account.balance)
    }

    @Test
    fun `given an expense, when editing without changes, then succeeds with identical values`() {
        val account = this.accountWithExpense(description = "Mercado")
        val original = account.expenses.first()

        val result = account.editExpense(
            expenseId = original.id,
            amount = original.amount.amount.toPlainString(),
            date = original.date.toString(),
            categoryId = original.categoryId,
            description = original.description,
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        val edited = (result as Outcome.Success).value
        assertEquals(original.id, edited.id)
        assertEquals(original.accountId, edited.accountId)
        assertEquals(original.amount, edited.amount)
        assertEquals(original.date, edited.date)
        assertEquals(original.categoryId, edited.categoryId)
        assertEquals(original.description, edited.description)
        assertEquals(original.createdAt, edited.createdAt)
    }

    @Test
    fun `given an expense, when editing with zero negative blank or non-numeric amount, then fails as creation does`() {
        val account = this.accountWithExpense()

        val zeroError = this.failureInvalidInput(this.editAmount(account, "0"))
        val negativeError = this.failureInvalidInput(this.editAmount(account, "-100"))
        val blankError = this.failureInvalidInput(this.editAmount(account, ""))
        val nonNumericError = this.failureInvalidInput(this.editAmount(account, "abc"))

        assertEquals("El monto debe ser mayor que cero.", zeroError.amountError)
        assertEquals("El monto debe ser mayor que cero.", negativeError.amountError)
        assertEquals("El monto es obligatorio.", blankError.amountError)
        assertEquals("El monto no es un número válido.", nonNumericError.amountError)
    }

    @Test
    fun `given an expense, when editing with a future date, then fails with a date error`() {
        val account = this.accountWithExpense()
        val original = account.expenses.first()

        val result = account.editExpense(
            expenseId = original.id,
            amount = "30000",
            date = "2099-01-01",
            categoryId = "cat-1",
            description = "",
            clock = this.fixedClock
        )

        val error = this.failureInvalidInput(result)
        assertEquals("La fecha debe ser hoy o en el pasado.", error.dateError)
        assertNull(error.amountError)
        assertNull(error.categoryError)
    }

    @Test
    fun `given an account created on March 1, when editing the date to February 28, then fails with date error`() {
        val account = this.accountWithExpense()
        val original = account.expenses.first()

        val result = account.editExpense(
            expenseId = original.id,
            amount = "30000",
            date = "2026-02-28",
            categoryId = "cat-1",
            description = "",
            clock = this.fixedClock
        )

        val error = this.failureInvalidInput(result)
        assertEquals("La fecha no puede ser anterior a la fecha de creación de la cuenta.", error.dateError)
    }

    @Test
    fun `given an account created on March 1, when editing the date to March 1, then succeeds`() {
        val account = this.accountWithExpense()
        val original = account.expenses.first()

        val result = account.editExpense(
            expenseId = original.id,
            amount = "30000",
            date = "2026-03-01",
            categoryId = "cat-1",
            description = "",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        assertEquals(LocalDate.parse("2026-03-01"), (result as Outcome.Success).value.date)
    }

    @Test
    fun `given an expense, when editing with blank amount date and category, then fails with all three errors`() {
        val account = this.accountWithExpense()
        val original = account.expenses.first()

        val result = account.editExpense(
            expenseId = original.id,
            amount = "",
            date = "",
            categoryId = "",
            description = "",
            clock = this.fixedClock
        )

        val error = this.failureInvalidInput(result)
        assertEquals("El monto es obligatorio.", error.amountError)
        assertEquals("La fecha es obligatoria.", error.dateError)
        assertEquals("La categoría es obligatoria.", error.categoryError)
    }

    @Test
    fun `given an expense with a description, when editing with a blank one, then it has an empty description`() {
        val account = this.accountWithExpense(description = "Mercado")
        val original = account.expenses.first()

        val result = account.editExpense(
            expenseId = original.id,
            amount = "30000",
            date = "2026-03-10",
            categoryId = "cat-1",
            description = "   ",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        assertEquals("", (result as Outcome.Success).value.description)
    }

    @Test
    fun `given an expense id not in the account, when editing, then fails with TransactionLookupError NotFound`() {
        val account = this.accountWithExpense()

        val result = account.editExpense(
            expenseId = "missing-expense",
            amount = "30000",
            date = "2026-03-10",
            categoryId = "cat-1",
            description = "",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
    }

    private fun accountWithExpense(description: String = ""): Account = AccountBuilder()
        .id("acc-1")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(Money.of(BigDecimal("100000.00"), Currency.COP))
        .withExpense(
            amount = "30000",
            date = "2026-03-10",
            categoryId = "cat-1",
            description = description,
            clock = this.expenseClock
        )
        .build()

    private fun editAmount(account: Account, amount: String): Outcome<Expense> = account.editExpense(
        expenseId = account.expenses.first().id,
        amount = amount,
        date = "2026-03-10",
        categoryId = "cat-1",
        description = "",
        clock = this.fixedClock
    )

    private fun failureInvalidInput(result: Outcome<Expense>): ExpenseUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.InvalidInput)
        return error as ExpenseUpdateError.InvalidInput
    }
}
