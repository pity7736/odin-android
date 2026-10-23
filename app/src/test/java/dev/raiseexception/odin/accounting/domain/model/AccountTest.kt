package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.ExpenseUpdateError
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.IncomeUpdateError
import dev.raiseexception.odin.accounting.domain.TransactionDeletionError
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
import kotlin.time.Duration.Companion.milliseconds

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
            kind = MoneyAccountKind.SAVINGS,
            description = "Fondo de emergencia",
            gmf = GmfTreatment.Exempt,
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
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
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
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
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
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El nombre es obligatorio.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a name longer than 200 characters, when creating an account, then returns name too long error`() {
        val result = Account.create(
            name = "a".repeat(MAX_NAME_LENGTH + 1),
            initialBalance = "10.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El nombre no puede superar los 200 caracteres.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a blank balance, when creating an account, then returns balance required error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El saldo inicial es obligatorio.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a non-numeric balance, when creating an account, then returns invalid number error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "abc",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El saldo inicial no es un número válido.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a negative balance, when creating an account, then returns negative balance error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "-1.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El saldo inicial no puede ser negativo.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a balance with more than two decimals, when creating an account, then returns decimals error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.255",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El saldo inicial admite máximo 2 decimales.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given no currency, when creating an account, then returns currency required error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = null,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("La moneda es obligatoria.", failureInvalidInput(result).currencyError)
    }

    @Test
    fun `given savings kind, when create, then funding is Funds with SAVINGS kind and type is SAVINGS`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.COP,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        val account = (result as Outcome.Success).value
        assertEquals(MoneyAccountKind.SAVINGS, (account.funding as AccountFunding.Funds).kind)
        assertEquals(AccountType.SAVINGS, account.type)
    }

    @Test
    fun `given cash kind, when create, then funding is Funds with CASH kind and type is CASH`() {
        val result = Account.create(
            name = "Efectivo",
            initialBalance = "10.00",
            currency = Currency.COP,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        val account = (result as Outcome.Success).value
        assertEquals(MoneyAccountKind.CASH, (account.funding as AccountFunding.Funds).kind)
        assertEquals(AccountType.CASH, account.type)
    }

    @Test
    fun `given no kind, when creating an account, then returns type required error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            kind = null,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El tipo de cuenta es obligatorio.", failureInvalidInput(result).typeError)
    }

    @Test
    fun `given a description longer than 500 characters, when creating, then returns description too long error`() {
        val result = Account.create(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "a".repeat(MAX_DESCRIPTION_LENGTH + 1),
            gmf = GmfTreatment.Exempt
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
            kind = MoneyAccountKind.CASH,
            description = "    ",
            gmf = GmfTreatment.Exempt
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
            kind = null,
            description = "",
            gmf = GmfTreatment.Exempt
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
            .kind(MoneyAccountKind.SAVINGS)
            .description("Fondo")
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .withIncome(amount = "500.00", date = "2026-01-01")
            .build()

        val result = original.edit(
            name = "Corriente",
            initialBalance = "2000.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "Gastos diarios",
            gmf = GmfTreatment.Exempt
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
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El nombre es obligatorio.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a name longer than 200 characters, when edit, then returns name too long error`() {
        val result = AccountBuilder().build().edit(
            name = "a".repeat(MAX_NAME_LENGTH + 1),
            initialBalance = "10.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El nombre no puede superar los 200 caracteres.", failureInvalidInput(result).nameError)
    }

    @Test
    fun `given a blank balance, when edit, then returns balance required error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El saldo inicial es obligatorio.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a negative balance, when edit, then returns negative balance error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "-1.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El saldo inicial no puede ser negativo.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given a balance with more than two decimals, when edit, then returns decimals error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.255",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El saldo inicial admite máximo 2 decimales.", failureInvalidInput(result).balanceError)
    }

    @Test
    fun `given no currency, when edit, then returns currency required error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = null,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("La moneda es obligatoria.", failureInvalidInput(result).currencyError)
    }

    @Test
    fun `given a money account, when edit with another kind, then funding carries the new kind and type follows`() {
        val original = AccountBuilder().kind(MoneyAccountKind.SAVINGS).build()

        val result = original.edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.COP,
            kind = MoneyAccountKind.CASH,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        val edited = (result as Outcome.Success).value
        assertEquals(MoneyAccountKind.CASH, (edited.funding as AccountFunding.Funds).kind)
        assertEquals(AccountType.CASH, edited.type)
    }

    @Test
    fun `given a credit card, when edit, then returns CreditCardNotEditable`() {
        val card = AccountBuilder()
            .creditCard(Money.of(BigDecimal("1000000"), Currency.COP), Money.of(BigDecimal("0"), Currency.COP))
            .build()

        val result = card.edit(
            name = "Visa",
            initialBalance = "10.00",
            currency = Currency.COP,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountUpdateError.CreditCardNotEditable)
    }

    @Test
    fun `given a credit card and invalid fields, when edit, then returns CreditCardNotEditable`() {
        val card = AccountBuilder()
            .creditCard(Money.of(BigDecimal("1000000"), Currency.COP), Money.of(BigDecimal("0"), Currency.COP))
            .build()

        val result = card.edit(
            name = "",
            initialBalance = "",
            currency = null,
            kind = null,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountUpdateError.CreditCardNotEditable)
    }

    @Test
    fun `given no kind, when edit, then returns type required error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            kind = null,
            description = "",
            gmf = GmfTreatment.Exempt
        )

        assertEquals("El tipo de cuenta es obligatorio.", failureInvalidInput(result).typeError)
    }

    @Test
    fun `given a description longer than 500 characters, when edit, then returns description too long error`() {
        val result = AccountBuilder().build().edit(
            name = "Ahorros",
            initialBalance = "10.00",
            currency = Currency.USD,
            kind = MoneyAccountKind.CASH,
            description = "a".repeat(MAX_DESCRIPTION_LENGTH + 1),
            gmf = GmfTreatment.Exempt
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
            kind = null,
            description = "",
            gmf = GmfTreatment.Exempt
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
            kind = MoneyAccountKind.CASH,
            description = "    ",
            gmf = GmfTreatment.Exempt
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
            funding = AccountFunding.Funds(initialBalance, MoneyAccountKind.SAVINGS, GmfTreatment.Exempt),
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

    @Test
    fun `given a restored credit card, when reading type, then is CREDIT_CARD`() {
        val account = Account.restore(
            id = "card-1",
            name = "Visa",
            funding = AccountFunding.Credit(
                Money.of(BigDecimal("1000000"), Currency.COP),
                Money.of(BigDecimal("0"), Currency.COP)
            ),
            description = "",
            createdAt = Instant.parse("2026-01-15T12:00:00Z")
        )

        assertEquals(AccountType.CREDIT_CARD, account.type)
    }

    @Test
    fun `given a restored savings funding, when reading type, then is SAVINGS`() {
        val account = Account.restore(
            id = "acc-1",
            name = "Ahorros",
            funding = AccountFunding.Funds(
                Money.of(BigDecimal("10.00"), Currency.COP),
                MoneyAccountKind.SAVINGS,
                GmfTreatment.Exempt
            ),
            description = "",
            createdAt = Instant.parse("2026-01-15T12:00:00Z")
        )

        assertEquals(AccountType.SAVINGS, account.type)
    }

    @Test
    fun `given a restored cash funding, when reading type, then is CASH`() {
        val account = Account.restore(
            id = "acc-1",
            name = "Efectivo",
            funding = AccountFunding.Funds(
                Money.of(BigDecimal("10.00"), Currency.COP),
                MoneyAccountKind.CASH,
                GmfTreatment.Exempt
            ),
            description = "",
            createdAt = Instant.parse("2026-01-15T12:00:00Z")
        )

        assertEquals(AccountType.CASH, account.type)
    }
}

class AccountCreateCreditCardTest {

    @Test
    fun `given a credit card, when created, then type is CREDIT_CARD`() {
        val result = Account.createCreditCard(
            name = "Visa",
            currency = Currency.COP,
            description = "",
            creditLimit = "1000000",
            existingDebt = ""
        )

        assertEquals(AccountType.CREDIT_CARD, (result as Outcome.Success).value.type)
    }

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
        assertEquals(Money.of(BigDecimal("500000"), Currency.COP), credit.initialDebt)
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
        assertEquals(0, credit.initialDebt.amount.compareTo(BigDecimal.ZERO))
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
        assertEquals(Money.of(BigDecimal("1000000"), Currency.COP), credit.initialDebt)
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
            tagIds = emptyList(),
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
        tagIds = emptyList(),
        clock = this.fixedClock
    )

    private fun failureInvalidInput(result: Outcome<Expense>): ExpenseUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.InvalidInput)
        return error as ExpenseUpdateError.InvalidInput
    }
}

class AccountEditIncomeTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }

    @Test
    fun `given an income, when editing every field validly, then returns it keeping id account and createdAt`() {
        val original = this.income("100000", "2026-03-05", description = "Salario")
        val savingsAccount = this.savingsAccount(incomes = listOf(original))

        val result = savingsAccount.editIncome(
            incomeId = original.id,
            amount = "120000",
            date = "2026-03-07",
            categoryId = "cat-income-2",
            description = "  Bono  ",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        val edited = (result as Outcome.Success).value
        assertEquals(original.id, edited.id)
        assertEquals("acc-1", edited.accountId)
        assertEquals(original.createdAt, edited.createdAt)
        assertEquals(this.pesos("120000"), edited.amount)
        assertEquals(LocalDate.parse("2026-03-07"), edited.date)
        assertEquals("cat-income-2", edited.categoryId)
        assertEquals("Bono", edited.description)
    }

    @Test
    fun `given an income, when editing it, then the account holds the edited income and the balance reflects it`() {
        val original = this.income("100000", "2026-03-05")
        val savingsAccount = this.savingsAccount(incomes = listOf(original))

        this.editIncome(savingsAccount, "40000", "2026-03-05")

        assertEquals(1, savingsAccount.incomes.size)
        assertEquals(original.id, savingsAccount.incomes.first().id)
        assertEquals(this.pesos("40000"), savingsAccount.incomes.first().amount)
        assertEquals(this.pesos("40000"), savingsAccount.balance)
    }

    @Test
    fun `given a balance of 20000 after an expense, when raising the income to 150000, then balance is 70000`() {
        val savingsAccount = this.accountWithIncomeAndExpense()

        val result = this.editIncome(savingsAccount, "150000", "2026-03-05")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("150000"), (result as Outcome.Success).value.amount)
        assertEquals(this.pesos("70000"), savingsAccount.balance)
    }

    @Test
    fun `given a balance of 20000 after an expense, when lowering the income to 90000, then balance is 10000`() {
        val savingsAccount = this.accountWithIncomeAndExpense()

        val result = this.editIncome(savingsAccount, "90000", "2026-03-05")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("10000"), savingsAccount.balance)
    }

    @Test
    fun `given a balance of 20000 after an expense, when lowering the income to 80000, then balance is 0`() {
        val savingsAccount = this.accountWithIncomeAndExpense()

        val result = this.editIncome(savingsAccount, "80000", "2026-03-05")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("0"), savingsAccount.balance)
    }

    @Test
    fun `given a balance of 20000 after an expense, when lowering the income to 79999, then an amount error`() {
        val savingsAccount = this.accountWithIncomeAndExpense()
        val original = savingsAccount.incomes.first()

        val result = this.editIncome(savingsAccount, "79999", "2026-03-05")

        val error = this.updateInvalidInput(result)
        assertEquals("El saldo de la cuenta no puede quedar negativo.", error.amountError)
        assertNull(error.dateError)
        assertNull(error.categoryError)
        assertEquals(listOf(original), savingsAccount.incomes)
        assertEquals(this.pesos("20000"), savingsAccount.balance)
    }

    @Test
    fun `given a later income covers today, when lowering the first below a March 8 expense, then a date error`() {
        val original = this.income("100000", "2026-03-05")
        val savingsAccount = this.savingsAccount(
            incomes = listOf(original, this.income("50000", "2026-03-10")),
            expenses = listOf(this.expense("80000", "2026-03-08"))
        )

        val result = savingsAccount.editIncome(
            incomeId = original.id,
            amount = "50000",
            date = "2026-03-05",
            categoryId = "cat-income",
            description = "",
            clock = this.fixedClock
        )

        val error = this.updateInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 8 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertEquals(original, savingsAccount.incomes.first())
    }

    @Test
    fun `given an income paying a March 8 expense, when moving it to March 10, then a date error`() {
        val savingsAccount = this.accountWithIncomeAndExpense()
        val original = savingsAccount.incomes.first()

        val result = this.editIncome(savingsAccount, "100000", "2026-03-10")

        val error = this.updateInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 8 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertEquals(listOf(original), savingsAccount.incomes)
    }

    @Test
    fun `given a balance of 20000, when editing the income to 50000 on March 10, then only the amount error`() {
        val savingsAccount = this.accountWithIncomeAndExpense()

        val result = this.editIncome(savingsAccount, "50000", "2026-03-10")

        val error = this.updateInvalidInput(result)
        assertEquals("El saldo de la cuenta no puede quedar negativo.", error.amountError)
        assertNull(error.dateError)
    }

    @Test
    fun `given an expense on March 10 recorded after the income, when moving the income there, then it is saved`() {
        val savingsAccount = this.savingsAccount(
            incomes = listOf(this.income("100000", "2026-03-05")),
            expenses = listOf(this.expense("80000", "2026-03-10"))
        )

        val result = this.editIncome(savingsAccount, "100000", "2026-03-10")

        assertTrue(result is Outcome.Success)
        assertEquals(LocalDate.parse("2026-03-10"), (result as Outcome.Success).value.date)
        assertEquals(this.pesos("20000"), savingsAccount.balance)
    }

    @Test
    fun `given an income, when editing without changes, then succeeds with identical values`() {
        val original = this.income("100000", "2026-03-05", description = "Salario")
        val savingsAccount = this.savingsAccount(incomes = listOf(original))

        val result = savingsAccount.editIncome(
            incomeId = original.id,
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
    fun `given an income with a description, when editing with a blank one, then it has an empty description`() {
        val original = this.income("100000", "2026-03-05", description = "Salario")
        val savingsAccount = this.savingsAccount(incomes = listOf(original))

        val result = savingsAccount.editIncome(
            incomeId = original.id,
            amount = "100000",
            date = "2026-03-05",
            categoryId = "cat-income",
            description = "   ",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        assertEquals("", (result as Outcome.Success).value.description)
    }

    @Test
    fun `given an income, when editing with zero negative blank or non-numeric amount, then fails as creation does`() {
        val savingsAccount = this.savingsAccount(incomes = listOf(this.income("100000", "2026-03-05")))

        val zeroError = this.updateInvalidInput(this.editIncome(savingsAccount, "0", "2026-03-05"))
        val negativeError = this.updateInvalidInput(this.editIncome(savingsAccount, "-100", "2026-03-05"))
        val blankError = this.updateInvalidInput(this.editIncome(savingsAccount, "", "2026-03-05"))
        val nonNumericError = this.updateInvalidInput(this.editIncome(savingsAccount, "abc", "2026-03-05"))

        assertEquals("El monto debe ser mayor que cero.", zeroError.amountError)
        assertEquals("El monto debe ser mayor que cero.", negativeError.amountError)
        assertEquals("El monto es obligatorio.", blankError.amountError)
        assertEquals("El monto no es un número válido.", nonNumericError.amountError)
    }

    @Test
    fun `given an income, when editing with a future date, then fails with a date error`() {
        val savingsAccount = this.savingsAccount(incomes = listOf(this.income("100000", "2026-03-05")))

        val error = this.updateInvalidInput(this.editIncome(savingsAccount, "100000", "2099-01-01"))

        assertEquals("La fecha debe ser hoy o en el pasado.", error.dateError)
        assertNull(error.amountError)
        assertNull(error.categoryError)
    }

    @Test
    fun `given an account created on March 1, when editing the income date to February 28, then a date error`() {
        val savingsAccount = this.savingsAccount(incomes = listOf(this.income("100000", "2026-03-05")))

        val error = this.updateInvalidInput(this.editIncome(savingsAccount, "100000", "2026-02-28"))

        assertEquals("La fecha no puede ser anterior a la fecha de creación de la cuenta.", error.dateError)
    }

    @Test
    fun `given an income, when editing with blank amount date and category, then fails with all three errors`() {
        val original = this.income("100000", "2026-03-05")
        val savingsAccount = this.savingsAccount(incomes = listOf(original))

        val result = savingsAccount.editIncome(
            incomeId = original.id,
            amount = "",
            date = "",
            categoryId = "",
            description = "",
            clock = this.fixedClock
        )

        val error = this.updateInvalidInput(result)
        assertEquals("El monto es obligatorio.", error.amountError)
        assertEquals("La fecha es obligatoria.", error.dateError)
        assertEquals("La categoría es obligatoria.", error.categoryError)
        assertEquals(listOf(original), savingsAccount.incomes)
    }

    @Test
    fun `given an income id not in the account, when editing, then fails with TransactionLookupError NotFound`() {
        val savingsAccount = this.savingsAccount(incomes = listOf(this.income("100000", "2026-03-05")))

        val result = savingsAccount.editIncome(
            incomeId = "missing-income",
            amount = "100000",
            date = "2026-03-05",
            categoryId = "cat-income",
            description = "",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransactionLookupError.NotFound)
        assertEquals("Transacción no encontrada", error.externalMessage)
    }

    private fun accountWithIncomeAndExpense(): Account = this.savingsAccount(
        incomes = listOf(this.income("100000", "2026-03-05")),
        expenses = listOf(this.expense("80000", "2026-03-08"))
    )

    private fun savingsAccount(
        incomes: List<Income> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): Account = AccountBuilder()
        .id("acc-1")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(this.pesos("0"))
        .incomes(incomes)
        .expenses(expenses)
        .build()

    private fun income(amount: String, date: String, description: String = ""): Income = Income.restore(
        id = "income-$amount-$date",
        accountId = "acc-1",
        amount = this.pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "cat-income",
        description = description,
        createdAt = Instant.parse("${date}T12:00:00Z")
    )

    private fun expense(amount: String, date: String): Expense = Expense.restore(
        id = "expense-$amount-$date",
        accountId = "acc-1",
        amount = this.pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "cat-1",
        description = "",
        createdAt = Instant.parse("${date}T12:00:00Z"),
        tagIds = emptyList(),
        gmfTax = null
    )

    private fun editIncome(account: Account, amount: String, date: String): Outcome<Income> = account.editIncome(
        incomeId = account.incomes.first().id,
        amount = amount,
        date = date,
        categoryId = "cat-income",
        description = "",
        clock = this.fixedClock
    )

    private fun updateInvalidInput(result: Outcome<Income>): IncomeUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeUpdateError.InvalidInput)
        return error as IncomeUpdateError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}

class AccountCreditCardExpenseTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val expenseCreatedAt = Instant.parse("2026-03-10T12:00:00Z")
    private val expenseClock = object : Clock {
        override fun now(): Instant = expenseCreatedAt
    }

    @Test
    fun `given a card with limit 3000000 and debt 500000, when creating expense 200000, then debt and credit update`() {
        val visaCard = this.visaCard()

        val result = this.createExpense(visaCard, "200000")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("700000"), this.currentDebt(visaCard))
        assertEquals(this.pesos("2300000"), this.availableCredit(visaCard))
    }

    @Test
    fun `given a card with available credit 2500000, when creating expense 2500001, then fails with cupo error`() {
        val visaCard = this.visaCard()

        val result = this.createExpense(visaCard, "2500001")

        val error = this.creationInvalidInput(result)
        assertEquals("El monto supera el cupo disponible.", error.amountError)
        assertNull(error.dateError)
        assertNull(error.categoryError)
        assertTrue(visaCard.expenses.isEmpty())
        assertEquals(this.pesos("500000"), this.currentDebt(visaCard))
    }

    @Test
    fun `given a card with 2500000 available, when creating expense 2500000, then succeeds with none left`() {
        val visaCard = this.visaCard()

        val result = this.createExpense(visaCard, "2500000")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("3000000"), this.currentDebt(visaCard))
        assertEquals(this.pesos("0"), this.availableCredit(visaCard))
    }

    @Test
    fun `given a card whose debt equals its limit, when creating expense 1, then fails with cupo error`() {
        val maxedOutCard = this.visaCard(initialDebt = "3000000")

        val result = this.createExpense(maxedOutCard, "1")

        assertEquals("El monto supera el cupo disponible.", this.creationInvalidInput(result).amountError)
        assertTrue(maxedOutCard.expenses.isEmpty())
    }

    @Test
    fun `given a card created on March 1, when creating an expense dated February 28, then fails with date error`() {
        val visaCard = this.visaCard()

        val result = this.createExpense(visaCard, "200000", date = "2026-02-28")

        val error = this.creationInvalidInput(result)
        assertEquals("La fecha no puede ser anterior a la fecha de creación de la cuenta.", error.dateError)
    }

    @Test
    fun `given a card created on March 1, when creating an expense dated March 1, then succeeds`() {
        val visaCard = this.visaCard()

        val result = this.createExpense(visaCard, "200000", date = "2026-03-01")

        assertTrue(result is Outcome.Success)
        assertEquals(LocalDate.parse("2026-03-01"), (result as Outcome.Success).value.date)
    }

    @Test
    fun `given a card, when creating expense with zero blank or non-numeric amount, then fails as a money account`() {
        val visaCard = this.visaCard()

        val zeroError = this.creationInvalidInput(this.createExpense(visaCard, "0"))
        val blankError = this.creationInvalidInput(this.createExpense(visaCard, ""))
        val nonNumericError = this.creationInvalidInput(this.createExpense(visaCard, "abc"))

        assertEquals("El monto debe ser mayor que cero.", zeroError.amountError)
        assertEquals("El monto es obligatorio.", blankError.amountError)
        assertEquals("El monto no es un número válido.", nonNumericError.amountError)
    }

    @Test
    fun `given a card with expense 300000, when editing it to 2500000, then succeeds as the edited one is excluded`() {
        val visaCard = this.visaCardWithExpense()

        val result = this.editAmount(visaCard, "2500000")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("3000000"), this.currentDebt(visaCard))
        assertEquals(this.pesos("0"), this.availableCredit(visaCard))
    }

    @Test
    fun `given a card with expense 300000, when editing it to 2500001, then fails with cupo error`() {
        val visaCard = this.visaCardWithExpense()

        val result = this.editAmount(visaCard, "2500001")

        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error as ExpenseUpdateError.InvalidInput
        assertEquals("El monto supera el cupo disponible.", error.amountError)
        assertEquals(this.pesos("800000"), this.currentDebt(visaCard))
    }

    @Test
    fun `given a card with limit 1000000 and expense 500000, when editing it to 1000001, then fails with cupo error`() {
        val card = this.millionCard(payments = emptyList())

        val result = this.editAmount(card, "1000001")

        val error = this.updateInvalidInput(result)
        assertEquals("El monto supera el cupo disponible.", error.amountError)
        assertEquals(this.pesos("500000"), card.expenses.first().amount)
    }

    @Test
    fun `given a card with expense 500000 and payment 400000, when editing it to 400000, then debt 0`() {
        val card = this.millionCard(payments = listOf(this.payment("400000")))

        val result = this.editAmount(card, "400000")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("400000"), card.expenses.first().amount)
        assertEquals(this.pesos("0"), this.currentDebt(card))
        assertEquals(this.pesos("1000000"), this.availableCredit(card))
    }

    @Test
    fun `given a card with expense 500000 and payment 400000, when editing it to 399999, then fails with debt error`() {
        val card = this.millionCard(payments = listOf(this.payment("400000")))

        val result = this.editAmount(card, "399999")

        val error = this.updateInvalidInput(result)
        assertEquals("La deuda no puede quedar negativa.", error.amountError)
        assertEquals(this.pesos("500000"), card.expenses.first().amount)
        assertEquals(this.pesos("100000"), this.currentDebt(card))
    }

    @Test
    fun `given a card expense 500000 paid 400000, when editing to 399999 with no category, then both errors`() {
        val card = this.millionCard(payments = listOf(this.payment("400000")))

        val result = card.editExpense(
            expenseId = card.expenses.first().id,
            amount = "399999",
            date = "2026-03-10",
            categoryId = "",
            description = "",
            tagIds = emptyList(),
            clock = this.fixedClock
        )

        val error = this.updateInvalidInput(result)
        assertEquals("La deuda no puede quedar negativa.", error.amountError)
        assertEquals("La categoría es obligatoria.", error.categoryError)
    }

    private fun millionCard(payments: List<Income>): Account = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .creditCard(creditLimit = this.pesos("1000000"), initialDebt = this.pesos("0"))
        .expenses(
            listOf(
                Expense.restore(
                    id = "expense-500000",
                    accountId = "card-1",
                    amount = this.pesos("500000"),
                    date = LocalDate.parse("2026-03-10"),
                    categoryId = "cat-1",
                    description = "",
                    createdAt = this.expenseCreatedAt,
                    tagIds = emptyList(),
                    gmfTax = null
                )
            )
        )
        .incomes(payments)
        .build()

    private fun payment(amount: String): Income = Income.restore(
        id = "payment-$amount",
        accountId = "card-1",
        amount = this.pesos(amount),
        date = LocalDate.parse("2026-03-11"),
        categoryId = "cat-payment",
        description = "",
        createdAt = Instant.parse("2026-03-11T12:00:00Z")
    )

    private fun updateInvalidInput(result: Outcome<Expense>): ExpenseUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.InvalidInput)
        return error as ExpenseUpdateError.InvalidInput
    }

    private fun visaCard(initialDebt: String = "500000"): Account = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .creditCard(creditLimit = this.pesos("3000000"), initialDebt = this.pesos(initialDebt))
        .build()

    private fun visaCardWithExpense(): Account = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .creditCard(creditLimit = this.pesos("3000000"), initialDebt = this.pesos("500000"))
        .withExpense(amount = "300000", date = "2026-03-10", categoryId = "cat-1", clock = this.expenseClock)
        .build()

    private fun createExpense(account: Account, amount: String, date: String = "2026-03-10"): Outcome<Expense> =
        account.createExpense(
            amount = amount,
            date = date,
            categoryId = "cat-1",
            description = "",
            tagIds = emptyList(),
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = this.fixedClock
        )

    private fun editAmount(account: Account, amount: String): Outcome<Expense> = account.editExpense(
        expenseId = account.expenses.first().id,
        amount = amount,
        date = "2026-03-10",
        categoryId = "cat-1",
        description = "",
        tagIds = emptyList(),
        clock = this.fixedClock
    )

    private fun currentDebt(account: Account): Money =
        (account.funding as AccountFunding.Credit).currentDebt(account.incomes, account.expenses)

    private fun availableCredit(account: Account): Money =
        (account.funding as AccountFunding.Credit).availableCredit(account.incomes, account.expenses)

    private fun creationInvalidInput(result: Outcome<Expense>): ExpenseCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        return error as ExpenseCreationError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}

class AccountCreditCardPaymentTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }

    @Test
    fun `given a card with debt 500, when creating an income of 500, then succeeds`() {
        val visaCard = this.visaCard(initialDebt = "500")

        val result = this.createIncome(visaCard, "500")

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a card with debt 500, when creating an income of 501, then fails with the payment exceeds debt error`() {
        val visaCard = this.visaCard(initialDebt = "500")

        val result = this.createIncome(visaCard, "501")

        val error = this.creationInvalidInput(result)
        assertEquals("El pago no puede superar la deuda actual.", error.amountError)
        assertTrue(visaCard.incomes.isEmpty())
    }

    @Test
    fun `given a card with no debt, when creating an income, then fails with the payment exceeds debt error`() {
        val visaCard = this.visaCard(initialDebt = "0")

        val result = this.createIncome(visaCard, "1")

        assertEquals("El pago no puede superar la deuda actual.", this.creationInvalidInput(result).amountError)
    }

    @Test
    fun `given a money account, when creating any valid income, then succeeds without a limit`() {
        val savingsAccount = AccountBuilder()
            .initialBalance(this.pesos("0"))
            .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
            .build()

        val result = this.createIncome(savingsAccount, "999999999")

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a card with debt 500 and limit 1000, when paying 200, then debt drops and freed credit can be spent`() {
        val visaCard = this.visaCard(initialDebt = "500")

        this.createIncome(visaCard, "200")
        val expenseResult = visaCard.createExpense(
            amount = "700",
            date = "2026-03-10",
            categoryId = "cat-1",
            description = "",
            tagIds = emptyList(),
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = this.fixedClock
        )

        assertEquals(this.pesos("1000"), visaCard.balance)
        assertTrue(expenseResult is Outcome.Success)
    }

    @Test
    fun `given a card, when creating an income with a blank or non-numeric amount, then reports the amount error`() {
        val visaCard = this.visaCard(initialDebt = "500")

        val blankError = this.creationInvalidInput(this.createIncome(visaCard, ""))
        val nonNumericError = this.creationInvalidInput(this.createIncome(visaCard, "abc"))

        assertEquals("El monto es obligatorio.", blankError.amountError)
        assertEquals("El monto no es un número válido.", nonNumericError.amountError)
    }

    private fun visaCard(initialDebt: String): Account = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .creditCard(creditLimit = this.pesos("1000"), initialDebt = this.pesos(initialDebt))
        .build()

    private fun createIncome(account: Account, amount: String): Outcome<Income> = account.createIncome(
        amount = amount,
        date = "2026-03-10",
        categoryId = "cat-1",
        description = "",
        clock = this.fixedClock
    )

    private fun creationInvalidInput(result: Outcome<Income>): IncomeCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeCreationError.InvalidInput)
        return error as IncomeCreationError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}

class AccountExpenseTagsTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val expenseCreatedAt = Instant.parse("2026-03-10T12:00:00Z")
    private val expenseClock = object : Clock {
        override fun now(): Instant = expenseCreatedAt
    }

    @Test
    fun `given two tag ids, when creating an expense, then it carries them in order`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "30000", listOf("tag-nala", "tag-comida"))

        assertEquals(listOf("tag-nala", "tag-comida"), (result as Outcome.Success).value.tagIds)
    }

    @Test
    fun `given duplicated tag ids, when creating an expense, then each is stored once in first occurrence order`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "30000", listOf("tag-nala", "tag-toby", "tag-nala"))

        assertEquals(listOf("tag-nala", "tag-toby"), (result as Outcome.Success).value.tagIds)
    }

    @Test
    fun `given five tag ids, when creating an expense, then it carries all five`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "30000", this.tagIds(FIVE_TAGS))

        assertEquals(this.tagIds(FIVE_TAGS), (result as Outcome.Success).value.tagIds)
    }

    @Test
    fun `given six distinct tag ids, when creating an expense, then fails with the tag limit error`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "30000", this.tagIds(SIX_TAGS))

        assertEquals("Máximo 5 etiquetas por gasto.", this.creationInvalidInput(result).tagsError)
        assertTrue(account.expenses.isEmpty())
    }

    @Test
    fun `given six tag ids where two repeat, when creating an expense, then succeeds with five`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "30000", this.tagIds(FIVE_TAGS) + "tag-1")

        assertEquals(this.tagIds(FIVE_TAGS), (result as Outcome.Success).value.tagIds)
    }

    @Test
    fun `given six tag ids and a blank amount, when creating an expense, then reports both errors at once`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "", this.tagIds(SIX_TAGS))

        val error = this.creationInvalidInput(result)
        assertEquals("El monto es obligatorio.", error.amountError)
        assertEquals("Máximo 5 etiquetas por gasto.", error.tagsError)
    }

    @Test
    fun `given a blank amount and one tag, when creating an expense, then reports no tags error`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "", listOf("tag-nala"))

        assertNull(this.creationInvalidInput(result).tagsError)
    }

    @Test
    fun `given no tag ids, when creating an expense, then it has no tags`() {
        val account = this.savingsAccount()

        val result = this.createExpense(account, "30000", emptyList())

        assertTrue((result as Outcome.Success).value.tagIds.isEmpty())
    }

    @Test
    fun `given a credit card, when creating an expense with a tag, then it carries the tag`() {
        val visaCard = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
            .creditCard(creditLimit = this.pesos("3000000"), initialDebt = this.pesos("0"))
            .build()

        val result = this.createExpense(visaCard, "30000", listOf("tag-gasolina"))

        assertEquals(listOf("tag-gasolina"), (result as Outcome.Success).value.tagIds)
    }

    @Test
    fun `given an expense with no tags, when editing it adding tags, then it carries the new tags`() {
        val account = this.accountWithTaggedExpense(emptyList())

        val result = this.editExpense(account, listOf("tag-carro", "tag-gasolina"))

        assertEquals(listOf("tag-carro", "tag-gasolina"), (result as Outcome.Success).value.tagIds)
        assertEquals(listOf("tag-carro", "tag-gasolina"), account.expenses.first().tagIds)
    }

    @Test
    fun `given an expense with two tags, when editing it removing one, then it keeps only the other`() {
        val account = this.accountWithTaggedExpense(listOf("tag-nala", "tag-toby"))

        val result = this.editExpense(account, listOf("tag-nala"))

        assertEquals(listOf("tag-nala"), (result as Outcome.Success).value.tagIds)
    }

    @Test
    fun `given an expense with a tag, when editing it removing every tag, then it has no tags`() {
        val account = this.accountWithTaggedExpense(listOf("tag-comida"))

        val result = this.editExpense(account, emptyList())

        assertTrue((result as Outcome.Success).value.tagIds.isEmpty())
    }

    @Test
    fun `given duplicated tag ids, when editing an expense, then each is stored once`() {
        val account = this.accountWithTaggedExpense(emptyList())

        val result = this.editExpense(account, listOf("tag-nala", "tag-nala"))

        assertEquals(listOf("tag-nala"), (result as Outcome.Success).value.tagIds)
    }

    @Test
    fun `given six distinct tag ids, when editing an expense, then fails with the tag limit error and keeps tags`() {
        val account = this.accountWithTaggedExpense(listOf("tag-nala"))

        val result = this.editExpense(account, this.tagIds(SIX_TAGS))

        assertEquals("Máximo 5 etiquetas por gasto.", this.updateInvalidInput(result).tagsError)
        assertEquals(listOf("tag-nala"), account.expenses.first().tagIds)
    }

    @Test
    fun `given six tag ids and a blank category, when editing an expense, then reports both errors at once`() {
        val account = this.accountWithTaggedExpense(emptyList())

        val result = account.editExpense(
            expenseId = account.expenses.first().id,
            amount = "30000",
            date = "2026-03-10",
            categoryId = "",
            description = "",
            tagIds = this.tagIds(SIX_TAGS),
            clock = this.fixedClock
        )

        val error = this.updateInvalidInput(result)
        assertEquals("La categoría es obligatoria.", error.categoryError)
        assertEquals("Máximo 5 etiquetas por gasto.", error.tagsError)
    }

    private fun savingsAccount(): Account = AccountBuilder()
        .id("acc-1")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(this.pesos("100000"))
        .build()

    private fun accountWithTaggedExpense(tagIds: List<String>): Account {
        val account = this.savingsAccount()
        account.createExpense(
            amount = "30000",
            date = "2026-03-10",
            categoryId = "cat-1",
            description = "",
            tagIds = tagIds,
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = this.expenseClock
        )
        return account
    }

    private fun createExpense(account: Account, amount: String, tagIds: List<String>): Outcome<Expense> =
        account.createExpense(
            amount = amount,
            date = "2026-03-10",
            categoryId = "cat-1",
            description = "",
            tagIds = tagIds,
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = this.fixedClock
        )

    private fun editExpense(account: Account, tagIds: List<String>): Outcome<Expense> = account.editExpense(
        expenseId = account.expenses.first().id,
        amount = "30000",
        date = "2026-03-10",
        categoryId = "cat-1",
        description = "",
        tagIds = tagIds,
        clock = this.fixedClock
    )

    private fun tagIds(count: Int): List<String> = (1..count).map { "tag-$it" }

    private fun creationInvalidInput(result: Outcome<Expense>): ExpenseCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        return error as ExpenseCreationError.InvalidInput
    }

    private fun updateInvalidInput(result: Outcome<Expense>): ExpenseUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.InvalidInput)
        return error as ExpenseUpdateError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)

    private companion object {
        const val FIVE_TAGS = 5
        const val SIX_TAGS = 6
    }
}

class AccountBackdatedMovementsTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }
    private val septemberInstant = Instant.parse("2026-09-30T12:00:00Z")
    private val septemberClock = object : Clock {
        override fun now(): Instant = septemberInstant
    }

    @Test
    fun `given 50000 and an income of 100000 on March 10, when spending 50000 on March 5, then it is saved`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "50000",
            incomes = listOf(this.income("100000", "2026-03-10"))
        )

        val result = this.createExpense(savingsAccount, "50000", "2026-03-05")

        assertTrue(result is Outcome.Success)
        assertEquals(1, savingsAccount.expenses.size)
        assertEquals(this.pesos("100000"), savingsAccount.balance)
    }

    @Test
    fun `given 0 and an income of 100000 on March 10, when spending 100000 on March 5, then a date error`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            incomes = listOf(this.income("100000", "2026-03-10"))
        )

        val result = this.createExpense(savingsAccount, "100000", "2026-03-05")

        val error = this.creationInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 5 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertTrue(savingsAccount.expenses.isEmpty())
    }

    @Test
    fun `given a history that breaks on March 7, when spending 40000 on March 4, then the message names March 7`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            incomes = listOf(this.income("50000", "2026-03-03"), this.income("100000", "2026-03-10")),
            expenses = listOf(this.expense("30000", "2026-03-07"))
        )

        val result = this.createExpense(savingsAccount, "40000", "2026-03-04")

        val error = this.creationInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 7 de marzo.", error.dateError)
        assertEquals(listOf("expense-30000"), savingsAccount.expenses.map { it.id })
    }

    @Test
    fun `given an account created in 2025, when spending on December 5 2025, then the message includes the year`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            createdAt = "2025-11-01T12:00:00Z",
            incomes = listOf(this.income("100000", "2026-01-10"))
        )

        val result = this.createExpense(savingsAccount, "100000", "2025-12-05")

        val error = this.creationInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 5 de diciembre de 2025.", error.dateError)
        assertTrue(savingsAccount.expenses.isEmpty())
    }

    @Test
    fun `given a balance of 50000 today, when spending 80000 on March 5, then only the amount error is shown`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            incomes = listOf(this.income("50000", "2026-03-10"))
        )

        val result = this.createExpense(savingsAccount, "80000", "2026-03-05")

        val error = this.creationInvalidInput(result)
        assertEquals("El monto supera el saldo disponible.", error.amountError)
        assertNull(error.dateError)
        assertTrue(savingsAccount.expenses.isEmpty())
    }

    @Test
    fun `given an income of 100000 on March 10, when spending 100000 on March 10, then it is saved after the income`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            incomes = listOf(this.income("100000", "2026-03-10"))
        )

        val result = this.createExpense(savingsAccount, "100000", "2026-03-10")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("0"), savingsAccount.balance)
    }

    @Test
    fun `given an expense recorded before an income on March 10, when editing it to 80000, then a date error`() {
        val original = this.expense("50000", "2026-03-10", "2026-03-10T10:00:00Z")
        val savingsAccount = this.savingsAccount(
            initialBalance = "50000",
            incomes = listOf(this.income("100000", "2026-03-10", "2026-03-10T11:00:00Z")),
            expenses = listOf(original)
        )

        val result = this.editExpense(savingsAccount, "80000", "2026-03-10")

        val error = this.updateInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 10 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertEquals(listOf(original), savingsAccount.expenses)
    }

    @Test
    fun `given an expense recorded before a March 15 income, when moving it there as 40000, then a date error`() {
        val original = this.expense("30000", "2026-03-20", "2026-03-20T12:00:00Z")
        val savingsAccount = this.savingsAccount(
            initialBalance = "30000",
            incomes = listOf(this.income("100000", "2026-03-15", "2026-03-21T12:00:00Z")),
            expenses = listOf(original)
        )

        val result = this.editExpense(savingsAccount, "40000", "2026-03-15")

        val error = this.updateInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 15 de marzo.", error.dateError)
        assertEquals(listOf(original), savingsAccount.expenses)
    }

    @Test
    fun `given a card at 800000 on March 5, when spending 300000 on March 7, then an over limit date error`() {
        val visaCard = this.millionCard(
            expenses = listOf(this.expense("800000", "2026-03-05")),
            payments = listOf(this.income("500000", "2026-03-10"))
        )

        val result = this.createExpense(visaCard, "300000", "2026-03-07")

        val error = this.creationInvalidInput(result)
        assertEquals("La deuda de la tarjeta superaría el cupo el 7 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertEquals(1, visaCard.expenses.size)
    }

    @Test
    fun `given a card expense of 500000 paid 400000 on March 8, when lowering it to 200000, then a date error`() {
        val original = this.expense("500000", "2026-03-05")
        val visaCard = this.millionCard(
            expenses = listOf(original, this.expense("300000", "2026-03-20")),
            payments = listOf(this.income("400000", "2026-03-08"))
        )

        val result = this.editExpense(visaCard, "200000", "2026-03-05")

        val error = this.updateInvalidInput(result)
        assertEquals("La deuda de la tarjeta quedaría negativa el 8 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertEquals(original, visaCard.expenses.first())
    }

    @Test
    fun `given a card expense of 500000 paid on March 8, when moving it to March 10, then a negative debt error`() {
        val original = this.expense("500000", "2026-03-05")
        val visaCard = this.millionCard(
            expenses = listOf(original, this.expense("200000", "2026-03-20")),
            payments = listOf(this.income("500000", "2026-03-08"))
        )

        val result = this.editExpense(visaCard, "500000", "2026-03-10")

        val error = this.updateInvalidInput(result)
        assertEquals("La deuda de la tarjeta quedaría negativa el 8 de marzo.", error.dateError)
        assertEquals(original, visaCard.expenses.first())
    }

    @Test
    fun `given a card debt of 500000 with 100000 spent by September 10, when paying 300000 then, then a date error`() {
        val visaCard = this.millionCard(
            createdAt = "2026-09-01T12:00:00Z",
            expenses = listOf(this.expense("100000", "2026-09-05"), this.expense("400000", "2026-09-20"))
        )

        val result = visaCard.createIncome(
            amount = "300000",
            date = "2026-09-10",
            categoryId = "cat-payment",
            description = "",
            clock = this.septemberClock
        )

        val error = this.incomeInvalidInput(result)
        assertEquals("La deuda de la tarjeta quedaría negativa el 10 de septiembre.", error.dateError)
        assertNull(error.amountError)
        assertTrue(visaCard.incomes.isEmpty())
    }

    @Test
    fun `given a card created with a debt of 200000, when paying 200000 before its later expense, then it is saved`() {
        val visaCard = this.millionCard(
            initialDebt = "200000",
            expenses = listOf(this.expense("300000", "2026-03-20"))
        )

        val result = visaCard.createIncome(
            amount = "200000",
            date = "2026-03-05",
            categoryId = "cat-payment",
            description = "",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("300000"), visaCard.balance)
    }

    @Test
    fun `given a balance of 50000 on the creation day, when spending 60000 that day, then a creation day date error`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "50000",
            incomes = listOf(this.income("100000", "2026-03-10"))
        )

        val result = this.createExpense(savingsAccount, "60000", "2026-03-01")

        val error = this.creationInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 1 de marzo.", error.dateError)
        assertTrue(savingsAccount.expenses.isEmpty())
    }

    @Test
    fun `given an account created on March 1, when spending on February 28, then only the creation date error`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            incomes = listOf(this.income("100000", "2026-03-10"))
        )

        val result = this.createExpense(savingsAccount, "100000", "2026-02-28")

        val error = this.creationInvalidInput(result)
        assertEquals("La fecha no puede ser anterior a la fecha de creación de la cuenta.", error.dateError)
        assertTrue(savingsAccount.expenses.isEmpty())
    }

    @Test
    fun `given a history breaking expense, when it also lacks a category and has six tags, then reports every error`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            incomes = listOf(this.income("100000", "2026-03-10"))
        )

        val result = savingsAccount.createExpense(
            amount = "100000",
            date = "2026-03-05",
            categoryId = "",
            description = "",
            tagIds = (1..6).map { "tag-$it" },
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = this.fixedClock
        )

        val error = this.creationInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 5 de marzo.", error.dateError)
        assertEquals("La categoría es obligatoria.", error.categoryError)
        assertEquals("Máximo 5 etiquetas por gasto.", error.tagsError)
    }

    @Test
    fun `given a card expense of 300 recorded at the same instant, when paying 300 that day, then it is saved`() {
        val visaCard = this.millionCard(
            expenses = listOf(this.expense("300", "2026-08-29", "2026-08-29T12:00:00Z"))
        )

        val result = visaCard.createIncome(
            amount = "300",
            date = "2026-08-29",
            categoryId = "cat-payment",
            description = "",
            clock = this.fixedClock
        )

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("0"), visaCard.balance)
    }

    @Test
    fun `given an income on March 10 recorded later than now, when spending it all that day, then it is saved last`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "0",
            incomes = listOf(this.income("100000", "2026-03-10", "2026-08-30T12:00:00Z"))
        )

        val result = this.createExpense(savingsAccount, "100000", "2026-03-10")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("0"), savingsAccount.balance)
    }

    private fun savingsAccount(
        initialBalance: String,
        createdAt: String = "2026-03-01T12:00:00Z",
        incomes: List<Income> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): Account = AccountBuilder()
        .id("acc-1")
        .createdAt(Instant.parse(createdAt))
        .initialBalance(this.pesos(initialBalance))
        .incomes(incomes)
        .expenses(expenses)
        .build()

    private fun millionCard(
        initialDebt: String = "0",
        createdAt: String = "2026-03-01T12:00:00Z",
        expenses: List<Expense> = emptyList(),
        payments: List<Income> = emptyList()
    ): Account = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .createdAt(Instant.parse(createdAt))
        .creditCard(creditLimit = this.pesos("1000000"), initialDebt = this.pesos(initialDebt))
        .expenses(expenses)
        .incomes(payments)
        .build()

    private fun income(amount: String, date: String, createdAt: String = "${date}T12:00:00Z"): Income =
        Income.restore(
            id = "income-$amount-$date",
            accountId = "acc-1",
            amount = this.pesos(amount),
            date = LocalDate.parse(date),
            categoryId = "cat-income",
            description = "",
            createdAt = Instant.parse(createdAt)
        )

    private fun expense(amount: String, date: String, createdAt: String = "${date}T12:00:00Z"): Expense =
        Expense.restore(
            id = "expense-$amount",
            accountId = "acc-1",
            amount = this.pesos(amount),
            date = LocalDate.parse(date),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse(createdAt),
            tagIds = emptyList(),
            gmfTax = null
        )

    private fun createExpense(account: Account, amount: String, date: String): Outcome<Expense> =
        account.createExpense(
            amount = amount,
            date = date,
            categoryId = "cat-1",
            description = "",
            tagIds = emptyList(),
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = this.fixedClock
        )

    private fun editExpense(account: Account, amount: String, date: String): Outcome<Expense> =
        account.editExpense(
            expenseId = account.expenses.first().id,
            amount = amount,
            date = date,
            categoryId = "cat-1",
            description = "",
            tagIds = emptyList(),
            clock = this.fixedClock
        )

    private fun creationInvalidInput(result: Outcome<Expense>): ExpenseCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        return error as ExpenseCreationError.InvalidInput
    }

    private fun updateInvalidInput(result: Outcome<Expense>): ExpenseUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.InvalidInput)
        return error as ExpenseUpdateError.InvalidInput
    }

    private fun incomeInvalidInput(result: Outcome<Income>): IncomeCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is IncomeCreationError.InvalidInput)
        return error as IncomeCreationError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}

class AccountDeleteTransactionTest {

    private val fixedInstant = Instant.parse("2026-08-29T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = fixedInstant
    }

    @Test
    fun `given a balance of 500000 and an expense of 30000, when deleting the expense, then balance is 530000`() {
        val lunch = this.expense("expense-lunch", "30000", "2026-03-05")
        val savingsAccount = this.savingsAccount(initialBalance = "530000", expenses = listOf(lunch))

        val result = savingsAccount.deleteExpense(lunch.id, this.fixedClock)

        assertEquals(Outcome.Success(Unit), result)
        assertTrue(savingsAccount.expenses.isEmpty())
        assertEquals(this.pesos("530000"), savingsAccount.balance)
    }

    @Test
    fun `given an account of 1000000 and an income of 2000000, when deleting the income, then balance is 1000000`() {
        val salary = this.income("income-salary", "2000000", "2026-03-05")
        val savingsAccount = this.savingsAccount(initialBalance = "1000000", incomes = listOf(salary))

        val result = savingsAccount.deleteIncome(salary.id, this.fixedClock)

        assertEquals(Outcome.Success(Unit), result)
        assertTrue(savingsAccount.incomes.isEmpty())
        assertEquals(this.pesos("1000000"), savingsAccount.balance)
    }

    @Test
    fun `given a card with a debt of 300000 from two expenses, when deleting the 100000 one, then debt is 200000`() {
        val smallPurchase = this.expense("expense-small", "100000", "2026-03-05")
        val largePurchase = this.expense("expense-large", "200000", "2026-03-06")
        val visaCard = this.millionCard(expenses = listOf(smallPurchase, largePurchase))
        val availableBefore = (visaCard.funding as AccountFunding.Credit).availableCredit(
            visaCard.incomes,
            visaCard.expenses
        )

        val result = visaCard.deleteExpense(smallPurchase.id, this.fixedClock)

        assertEquals(Outcome.Success(Unit), result)
        assertEquals(listOf(largePurchase), visaCard.expenses)
        assertEquals(this.pesos("200000"), visaCard.balance)
        val availableAfter = (visaCard.funding as AccountFunding.Credit).availableCredit(
            visaCard.incomes,
            visaCard.expenses
        )
        assertEquals(availableBefore.amount.add(BigDecimal("100000")), availableAfter.amount)
    }

    @Test
    fun `given an income paying a March 8 expense, when deleting the income, then a history breach naming March 8`() {
        val payday = this.income("income-payday", "1000000", "2026-03-05")
        val rent = this.expense("expense-rent", "800000", "2026-03-08")
        val savingsAccount = this.savingsAccount(incomes = listOf(payday), expenses = listOf(rent))

        val result = savingsAccount.deleteIncome(payday.id, this.fixedClock)

        val error = this.historyBreach(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 8 de marzo.", error.externalMessage)
        assertEquals("Deleting the transaction breaks the account history", error.internalMessage)
        assertEquals(listOf(payday), savingsAccount.incomes)
        assertEquals(listOf(rent), savingsAccount.expenses)
        assertEquals(this.pesos("200000"), savingsAccount.balance)
    }

    @Test
    fun `given a card expense covered by a March 8 payment, when deleting it, then a history breach naming March 8`() {
        val purchase = this.expense("expense-purchase", "500000", "2026-03-05")
        val payment = this.income("income-payment", "500000", "2026-03-08")
        val visaCard = this.millionCard(expenses = listOf(purchase), payments = listOf(payment))

        val result = visaCard.deleteExpense(purchase.id, this.fixedClock)

        val error = this.historyBreach(result)
        assertEquals("La deuda de la tarjeta quedaría negativa el 8 de marzo.", error.externalMessage)
        assertEquals(listOf(purchase), visaCard.expenses)
        assertEquals(listOf(payment), visaCard.incomes)
        assertEquals(this.pesos("0"), visaCard.balance)
    }

    @Test
    fun `given two wrong incomes paying a March 8 expense, when deleting either alone, then both are rejected`() {
        val firstWrongIncome = this.income("income-wrong-first", "500000", "2026-03-05")
        val secondWrongIncome = this.income("income-wrong-second", "500000", "2026-03-05")
        val savingsAccount = this.savingsAccount(
            incomes = listOf(firstWrongIncome, secondWrongIncome),
            expenses = listOf(this.expense("expense-rent", "800000", "2026-03-08"))
        )

        val firstResult = savingsAccount.deleteIncome(firstWrongIncome.id, this.fixedClock)
        val secondResult = savingsAccount.deleteIncome(secondWrongIncome.id, this.fixedClock)

        this.historyBreach(firstResult)
        this.historyBreach(secondResult)
        assertEquals(listOf(firstWrongIncome, secondWrongIncome), savingsAccount.incomes)
    }

    @Test
    fun `given two wrong incomes, when recording the real one and deleting both, then balance is 200000`() {
        val firstWrongIncome = this.income("income-wrong-first", "500000", "2026-03-05")
        val secondWrongIncome = this.income("income-wrong-second", "500000", "2026-03-05")
        val savingsAccount = this.savingsAccount(
            incomes = listOf(firstWrongIncome, secondWrongIncome),
            expenses = listOf(this.expense("expense-rent", "800000", "2026-03-08"))
        )
        savingsAccount.createIncome(
            amount = "1000000",
            date = "2026-03-05",
            categoryId = "cat-income",
            description = "",
            clock = this.fixedClock
        )

        val firstResult = savingsAccount.deleteIncome(firstWrongIncome.id, this.fixedClock)
        val secondResult = savingsAccount.deleteIncome(secondWrongIncome.id, this.fixedClock)

        assertEquals(Outcome.Success(Unit), firstResult)
        assertEquals(Outcome.Success(Unit), secondResult)
        assertEquals(listOf(this.pesos("1000000")), savingsAccount.incomes.map { it.amount })
        assertEquals(this.pesos("200000"), savingsAccount.balance)
    }

    @Test
    fun `given an id not in the account, when deleting an income, then fails with TransactionLookupError NotFound`() {
        val savingsAccount = this.savingsAccount(incomes = listOf(this.income("income-salary", "100000", "2026-03-05")))

        val result = savingsAccount.deleteIncome("missing-income", this.fixedClock)

        this.assertNotFound(result)
        assertEquals(1, savingsAccount.incomes.size)
    }

    @Test
    fun `given an id not in the account, when deleting an expense, then fails with TransactionLookupError NotFound`() {
        val savingsAccount = this.savingsAccount(
            initialBalance = "100000",
            expenses = listOf(this.expense("expense-lunch", "30000", "2026-03-05"))
        )

        val result = savingsAccount.deleteExpense("missing-expense", this.fixedClock)

        this.assertNotFound(result)
        assertEquals(1, savingsAccount.expenses.size)
    }

    @Test
    fun `given an expense id, when deleting it as an income, then fails with TransactionLookupError NotFound`() {
        val lunch = this.expense("expense-lunch", "30000", "2026-03-05")
        val savingsAccount = this.savingsAccount(initialBalance = "100000", expenses = listOf(lunch))

        val result = savingsAccount.deleteIncome(lunch.id, this.fixedClock)

        this.assertNotFound(result)
        assertEquals(listOf(lunch), savingsAccount.expenses)
    }

    @Test
    fun `given an income id, when deleting it as an expense, then fails with TransactionLookupError NotFound`() {
        val salary = this.income("income-salary", "100000", "2026-03-05")
        val savingsAccount = this.savingsAccount(incomes = listOf(salary))

        val result = savingsAccount.deleteExpense(salary.id, this.fixedClock)

        this.assertNotFound(result)
        assertEquals(listOf(salary), savingsAccount.incomes)
    }

    private fun savingsAccount(
        initialBalance: String = "0",
        incomes: List<Income> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): Account = AccountBuilder()
        .id("acc-1")
        .name("Ahorros")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(this.pesos(initialBalance))
        .incomes(incomes)
        .expenses(expenses)
        .build()

    private fun millionCard(
        expenses: List<Expense> = emptyList(),
        payments: List<Income> = emptyList()
    ): Account = AccountBuilder()
        .id("acc-1")
        .name("Visa")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .creditCard(creditLimit = this.pesos("1000000"), initialDebt = this.pesos("0"))
        .expenses(expenses)
        .incomes(payments)
        .build()

    private fun income(id: String, amount: String, date: String): Income = Income.restore(
        id = id,
        accountId = "acc-1",
        amount = this.pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "cat-income",
        description = "",
        createdAt = Instant.parse("${date}T12:00:00Z")
    )

    private fun expense(id: String, amount: String, date: String): Expense = Expense.restore(
        id = id,
        accountId = "acc-1",
        amount = this.pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "cat-1",
        description = "",
        createdAt = Instant.parse("${date}T12:00:00Z"),
        tagIds = emptyList(),
        gmfTax = null
    )

    private fun historyBreach(result: Outcome<Unit>): TransactionDeletionError.HistoryBreach {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransactionDeletionError.HistoryBreach)
        return error as TransactionDeletionError.HistoryBreach
    }

    private fun assertNotFound(result: Outcome<Unit>) {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is TransactionLookupError.NotFound)
        assertEquals("Transacción no encontrada", error.externalMessage)
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}

class AccountGmfSettingTest {

    private val impuestosTreatment = GmfTreatment.Taxed(categoryId = "cat-impuestos", tagNames = listOf("4x1000"))

    @Test
    fun `given a savings account in COP marked as not exempt, when creating it, then it keeps the tax settings`() {
        val result = this.create(MoneyAccountKind.SAVINGS, Currency.COP, this.impuestosTreatment)

        assertEquals(this.impuestosTreatment, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a savings account in COP left exempt, when creating it, then it is exempt`() {
        val result = this.create(MoneyAccountKind.SAVINGS, Currency.COP, GmfTreatment.Exempt)

        assertEquals(GmfTreatment.Exempt, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a cash account marked as not exempt, when creating it, then it is saved as exempt`() {
        val result = this.create(MoneyAccountKind.CASH, Currency.COP, this.impuestosTreatment)

        assertEquals(GmfTreatment.Exempt, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a savings account in USD marked as not exempt, when creating it, then it is saved as exempt`() {
        val result = this.create(MoneyAccountKind.SAVINGS, Currency.USD, this.impuestosTreatment)

        assertEquals(GmfTreatment.Exempt, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a savings account in EUR marked as not exempt, when creating it, then it is saved as exempt`() {
        val result = this.create(MoneyAccountKind.SAVINGS, Currency.EUR, this.impuestosTreatment)

        assertEquals(GmfTreatment.Exempt, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a non-exempt account with no tax category, when creating it, then a tax category required error`() {
        val result = this.create(MoneyAccountKind.SAVINGS, Currency.COP, GmfTreatment.Taxed(" ", emptyList()))

        assertEquals("La categoría del 4x1000 es obligatoria.", this.creationInvalidInput(result).gmfCategoryError)
    }

    @Test
    fun `given a non-exempt account with six tax tags, when creating it, then the tag limit error`() {
        val treatment = GmfTreatment.Taxed("cat-impuestos", (1..Expense.MAX_TAGS + 1).map { "tag-$it" })

        val result = this.create(MoneyAccountKind.SAVINGS, Currency.COP, treatment)

        assertEquals(Expense.TAG_LIMIT_MESSAGE, this.creationInvalidInput(result).gmfTagsError)
    }

    @Test
    fun `given a non-exempt account with five tax tags, when creating it, then it keeps the five`() {
        val treatment = GmfTreatment.Taxed("cat-impuestos", (1..Expense.MAX_TAGS).map { "tag-$it" })

        val result = this.create(MoneyAccountKind.SAVINGS, Currency.COP, treatment)

        assertEquals(treatment, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a blank name and no tax category, when creating, then reports both errors at once`() {
        val result = Account.create(
            name = "",
            initialBalance = "1000",
            currency = Currency.COP,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfTreatment.Taxed("", emptyList())
        )

        val error = this.creationInvalidInput(result)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertEquals("La categoría del 4x1000 es obligatoria.", error.gmfCategoryError)
    }

    @Test
    fun `given a savings account in COP, when editing it as not exempt, then it keeps the tax settings`() {
        val result = this.edit(this.ahorros(GmfTreatment.Exempt), MoneyAccountKind.SAVINGS, this.impuestosTreatment)

        assertEquals(this.impuestosTreatment, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a non-exempt account, when saving it as exempt, then it discards the tax settings`() {
        val result = this.edit(this.ahorros(this.impuestosTreatment), MoneyAccountKind.SAVINGS, GmfTreatment.Exempt)

        assertEquals(GmfTreatment.Exempt, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a non-exempt account with taxes, when changing its type to cash, then discards settings keeps taxes`() {
        val ahorros = AccountBuilder()
            .initialBalance(Money.of(BigDecimal("1000000"), Currency.COP))
            .gmf(this.impuestosTreatment)
            .withExpense(amount = "100000", date = "2026-01-01")
            .build()

        val result = this.edit(ahorros, MoneyAccountKind.CASH, this.impuestosTreatment)

        val edited = (result as Outcome.Success).value
        assertEquals(GmfTreatment.Exempt, this.fundsOf(result).gmf)
        assertEquals(ahorros.expenses.map { it.id }, edited.expenses.map { it.id })
    }

    @Test
    fun `given a non-exempt account, when changing its currency away from COP, then it is saved as exempt`() {
        val result = this.ahorros(this.impuestosTreatment).edit(
            name = "Ahorros",
            initialBalance = "1000",
            currency = Currency.USD,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = this.impuestosTreatment
        )

        assertEquals(GmfTreatment.Exempt, this.fundsOf(result).gmf)
    }

    @Test
    fun `given a savings account in COP, when editing it as not exempt without a category, then a category error`() {
        val treatment = GmfTreatment.Taxed("", emptyList())

        val result = this.edit(this.ahorros(GmfTreatment.Exempt), MoneyAccountKind.SAVINGS, treatment)

        assertEquals("La categoría del 4x1000 es obligatoria.", this.updateInvalidInput(result).gmfCategoryError)
    }

    @Test
    fun `given a savings account in COP, when editing it with six tax tags, then the tag limit error`() {
        val treatment = GmfTreatment.Taxed("cat-impuestos", (1..Expense.MAX_TAGS + 1).map { "tag-$it" })

        val result = this.edit(this.ahorros(GmfTreatment.Exempt), MoneyAccountKind.SAVINGS, treatment)

        assertEquals(Expense.TAG_LIMIT_MESSAGE, this.updateInvalidInput(result).gmfTagsError)
    }

    @Test
    fun `given a blank name and no tax category, when editing, then reports both errors at once`() {
        val result = this.ahorros(GmfTreatment.Exempt).edit(
            name = "",
            initialBalance = "1000",
            currency = Currency.COP,
            kind = MoneyAccountKind.SAVINGS,
            description = "",
            gmf = GmfTreatment.Taxed("", emptyList())
        )

        val error = this.updateInvalidInput(result)
        assertEquals("El nombre es obligatorio.", error.nameError)
        assertEquals("La categoría del 4x1000 es obligatoria.", error.gmfCategoryError)
    }

    private fun create(kind: MoneyAccountKind, currency: Currency, gmf: GmfTreatment): Outcome<Account> =
        Account.create(
            name = "Ahorros",
            initialBalance = "1000",
            currency = currency,
            kind = kind,
            description = "",
            gmf = gmf
        )

    private fun edit(account: Account, kind: MoneyAccountKind, gmf: GmfTreatment): Outcome<Account> =
        account.edit(
            name = "Ahorros",
            initialBalance = "1000000",
            currency = Currency.COP,
            kind = kind,
            description = "",
            gmf = gmf
        )

    private fun ahorros(gmf: GmfTreatment): Account = AccountBuilder()
        .initialBalance(Money.of(BigDecimal("1000000"), Currency.COP))
        .gmf(gmf)
        .build()

    private fun fundsOf(result: Outcome<Account>): AccountFunding.Funds =
        (result as Outcome.Success).value.funding as AccountFunding.Funds

    private fun creationInvalidInput(result: Outcome<Account>): AccountCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is AccountCreationError.InvalidInput)
        return error as AccountCreationError.InvalidInput
    }

    private fun updateInvalidInput(result: Outcome<Account>): AccountUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is AccountUpdateError.InvalidInput)
        return error as AccountUpdateError.InvalidInput
    }
}

class AccountGmfExpenseTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@AccountGmfExpenseTest.now
    }
    private val today = "2026-10-22"
    private val impuestos = GmfTreatment.Taxed(categoryId = "cat-impuestos", tagNames = listOf("4x1000"))

    @Test
    fun `given Ahorros with 1000000, when recording Mercado of 100000, then Mercado carries its tax of 400`() {
        val ahorros = this.ahorros("1000000")

        val mercado = this.success(this.record(ahorros, "100000", "Mercado", gmfTagIds = listOf("tag-4x1000")))

        val tax = mercado.gmfTax!!
        assertEquals("Mercado", mercado.description)
        assertEquals(this.pesos("100000"), mercado.amount)
        assertEquals(this.pesos("400.00"), tax.amount)
        assertEquals("4x1000 – Mercado", tax.description)
        assertEquals("cat-impuestos", tax.categoryId)
        assertEquals(listOf("tag-4x1000"), tax.tagIds)
        assertEquals(mercado.date, tax.date)
        assertEquals(ahorros.id, tax.accountId)
        assertEquals(mercado.createdAt + 1.milliseconds, tax.createdAt)
        assertNull(tax.gmfTax)
    }

    @Test
    fun `given Ahorros not exempt, when asking the tax of 100000, then it is 400`() {
        assertEquals(BigDecimal("400.00"), this.ahorros("1000000").gmfTaxOn("100000"))
    }

    @Test
    fun `given Ahorros not exempt, when asking the tax of 1, then it is zero`() {
        assertEquals(0, this.ahorros("1000000").gmfTaxOn("1.00").signum())
    }

    @Test
    fun `given Ahorros not exempt, when asking the tax of an invalid amount, then it is zero`() {
        assertEquals(0, this.ahorros("1000000").gmfTaxOn("abc").signum())
    }

    @Test
    fun `given an exempt account, when asking the tax of 100000, then it is zero`() {
        assertEquals(0, AccountBuilder().build().gmfTaxOn("100000").signum())
    }

    @Test
    fun `given Ahorros with 1000000, when recording Mercado of 100000, then the balance is 899600`() {
        val ahorros = this.ahorros("1000000")

        this.record(ahorros, "100000", "Mercado")

        assertEquals(this.pesos("899600"), ahorros.balance)
    }

    @Test
    fun `given Ahorros, when recording Mercado, then its movements list Mercado followed by its tax`() {
        val ahorros = this.ahorros("1000000")

        val mercado = this.success(this.record(ahorros, "100000", "Mercado"))

        assertEquals(listOf(mercado.id, mercado.gmfTax!!.id), ahorros.expenses.map { it.id })
        assertEquals(listOf(mercado.id, mercado.gmfTax!!.id), ahorros.transactions.map { it.id })
    }

    @Test
    fun `given an exempt account Nomina with 1000000, when recording 100000, then only the expense is recorded`() {
        val nomina = AccountBuilder()
            .name("Nómina")
            .initialBalance(this.pesos("1000000"))
            .createdAt(Instant.parse("2026-01-01T12:00:00Z"))
            .build()

        val expense = this.success(this.record(nomina, "100000", "Mercado"))

        assertNull(expense.gmfTax)
        assertEquals(1, nomina.expenses.size)
        assertEquals(this.pesos("900000"), nomina.balance)
    }

    @Test
    fun `given Ahorros, when recording an expense without charging the tax, then no tax is recorded`() {
        val ahorros = this.ahorros("1000000")

        val expense = this.success(
            ahorros.createExpense(
                amount = "200000",
                date = this.today,
                categoryId = "cat-transferencia",
                description = "Transferencia a Corriente",
                tagIds = emptyList(),
                chargeGmf = false,
                gmfTagIds = listOf("tag-4x1000"),
                clock = this.clock
            )
        )

        assertNull(expense.gmfTax)
        assertEquals(this.pesos("800000"), ahorros.balance)
    }

    @Test
    fun `given Ahorros, when recording an expense of 100000 with no description, then its tax is named 4x1000`() {
        val ahorros = this.ahorros("1000000")

        val expense = this.success(this.record(ahorros, "100000", "   "))

        assertEquals("4x1000", expense.gmfTax!!.description)
    }

    @Test
    fun `given Ahorros, when recording Veterinario tagged Nala, then its tax has the tag 4x1000 and not Nala`() {
        val ahorros = this.ahorros("1000000")

        val veterinario = this.success(
            ahorros.createExpense(
                amount = "100000",
                date = this.today,
                categoryId = "cat-mascotas",
                description = "Veterinario",
                tagIds = listOf("tag-nala"),
                chargeGmf = true,
                gmfTagIds = listOf("tag-4x1000"),
                clock = this.clock
            )
        )

        assertEquals(listOf("tag-nala"), veterinario.tagIds)
        assertEquals("4x1000 – Veterinario", veterinario.gmfTax!!.description)
        assertEquals(listOf("tag-4x1000"), veterinario.gmfTax!!.tagIds)
    }

    @Test
    fun `given Ahorros, when recording an expense of 12345,67, then its tax is rounded to the cent as 49,38`() {
        val expense = this.success(this.record(this.ahorros("1000000"), "12345.67", "Mercado"))

        assertEquals(this.pesos("49.38"), expense.gmfTax!!.amount)
    }

    @Test
    fun `given Ahorros, when recording an expense of 13,75, then a half cent rounds its tax up to 0,06`() {
        val expense = this.success(this.record(this.ahorros("1000000"), "13.75", "Mercado"))

        assertEquals(this.pesos("0.06"), expense.gmfTax!!.amount)
    }

    @Test
    fun `given Ahorros, when recording an expense of 1, then no tax is recorded because it rounds to zero`() {
        val ahorros = this.ahorros("1000000")

        val expense = this.success(this.record(ahorros, "1.00", "Chicle"))

        assertNull(expense.gmfTax)
        assertEquals(1, ahorros.expenses.size)
    }

    @Test
    fun `given Ahorros with 100000, when recording an expense of 100000, then the amount plus tax error`() {
        val ahorros = this.ahorros("100000")

        val result = this.record(ahorros, "100000", "Mercado")

        val error = this.creationInvalidInput(result)
        assertEquals("El monto más el 4x1000 supera el saldo disponible.", error.amountError)
        assertTrue(ahorros.expenses.isEmpty())
    }

    @Test
    fun `given Ahorros with 100000, when recording an expense of 99601, then it and its tax of 398,40 fit`() {
        val ahorros = this.ahorros("100000")

        val expense = this.success(this.record(ahorros, "99601", "Mercado"))

        assertEquals(this.pesos("398.40"), expense.gmfTax!!.amount)
        assertEquals(this.pesos("0.60"), ahorros.balance)
    }

    @Test
    fun `given Ahorros with 100000, when recording an expense of 150000, then the amount alone exceeds the balance`() {
        val result = this.record(this.ahorros("100000"), "150000", "Mercado")

        assertEquals("El monto supera el saldo disponible.", this.creationInvalidInput(result).amountError)
    }

    @Test
    fun `given incomes on March 5 and 10, when recording 99800 on March 6, then its tax breaks the history`() {
        val ahorros = this.marchAhorros(
            this.income("100000", "2026-03-05"),
            this.income("500000", "2026-03-10")
        )

        val result = this.record(ahorros, "99800", "Mercado", date = "2026-03-06")

        val error = this.creationInvalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 6 de marzo.", error.dateError)
        assertNull(error.amountError)
    }

    @Test
    fun `given an income on March 10 recorded later than now, when spending it with its tax that day, then saved`() {
        val ahorros = this.marchAhorros(
            this.income("100400", "2026-03-10", createdAt = "2026-10-22T13:00:00Z")
        )

        val result = this.record(ahorros, "100000", "Mercado", date = "2026-03-10")

        assertTrue(result is Outcome.Success)
        assertEquals(this.pesos("0.00"), ahorros.balance)
    }

    @Test
    fun `given Ahorros not exempt today, when recording Arriendo dated September 15, then its tax has that date`() {
        val ahorros = this.ahorros("2000000")

        val arriendo = this.success(this.record(ahorros, "1000000", "Arriendo", date = "2026-09-15"))

        assertEquals(this.pesos("4000.00"), arriendo.gmfTax!!.amount)
        assertEquals("4x1000 – Arriendo", arriendo.gmfTax!!.description)
        assertEquals(LocalDate.parse("2026-09-15"), arriendo.gmfTax!!.date)
    }

    @Test
    fun `given a credit card, when recording an expense, then it is never taxed`() {
        val visa = AccountBuilder()
            .creditCard(this.pesos("3000000"), this.pesos("0"))
            .createdAt(Instant.parse("2026-01-01T12:00:00Z"))
            .build()

        val expense = this.success(this.record(visa, "100000", "Mercado"))

        assertNull(expense.gmfTax)
    }

    private fun ahorros(initialBalance: String): Account = AccountBuilder()
        .name("Ahorros")
        .initialBalance(this.pesos(initialBalance))
        .gmf(this.impuestos)
        .createdAt(Instant.parse("2026-01-01T12:00:00Z"))
        .build()

    private fun marchAhorros(vararg incomes: Income): Account = AccountBuilder()
        .id("ahorros")
        .name("Ahorros")
        .initialBalance(this.pesos("0"))
        .gmf(this.impuestos)
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .incomes(incomes.toList())
        .build()

    private fun income(amount: String, date: String, createdAt: String = "${date}T12:00:00Z"): Income =
        Income.restore(
            id = "income-$amount",
            accountId = "ahorros",
            amount = this.pesos(amount),
            date = LocalDate.parse(date),
            categoryId = "cat-salario",
            description = "",
            createdAt = Instant.parse(createdAt)
        )

    private fun record(
        account: Account,
        amount: String,
        description: String,
        date: String = this.today,
        gmfTagIds: List<String> = emptyList()
    ): Outcome<Expense> = account.createExpense(
        amount = amount,
        date = date,
        categoryId = "cat-mercado",
        description = description,
        tagIds = emptyList(),
        chargeGmf = true,
        gmfTagIds = gmfTagIds,
        clock = this.clock
    )

    private fun success(result: Outcome<Expense>): Expense = (result as Outcome.Success).value

    private fun creationInvalidInput(result: Outcome<Expense>): ExpenseCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseCreationError.InvalidInput)
        return error as ExpenseCreationError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}

class AccountGmfExpenseEditTest {

    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@AccountGmfExpenseEditTest.now
    }
    private val impuestos = GmfTreatment.Taxed(categoryId = "cat-impuestos", tagNames = listOf("4x1000"))

    @Test
    fun `given Mercado of 100000 with its tax of 400, when editing it to 50000, then its tax is 200`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado")

        val edited = this.success(this.edit(ahorros, mercado.id, "50000"))

        assertEquals(this.pesos("200.00"), edited.gmfTax!!.amount)
        assertEquals("4x1000 – Mercado", edited.gmfTax!!.description)
        assertEquals(this.pesos("949800"), ahorros.balance)
    }

    @Test
    fun `given Mercado with its tax, when editing it, then the tax keeps its id createdAt category and tags`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado")

        val edited = this.success(this.edit(ahorros, mercado.id, "50000"))

        val tax = edited.gmfTax!!
        assertEquals(mercado.gmfTax!!.id, tax.id)
        assertEquals(mercado.gmfTax!!.createdAt, tax.createdAt)
        assertEquals("cat-impuestos", tax.categoryId)
        assertEquals(listOf("tag-4x1000"), tax.tagIds)
        assertEquals(listOf(mercado.id, tax.id), ahorros.expenses.map { it.id })
    }

    @Test
    fun `given Mercado dated October 10 with its tax, when editing it to October 8, then its tax is dated October 8`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado", date = "2026-10-10")

        val edited = this.success(this.edit(ahorros, mercado.id, "100000", date = "2026-10-08"))

        assertEquals(LocalDate.parse("2026-10-08"), edited.gmfTax!!.date)
    }

    @Test
    fun `given Mercado with its tax, when editing its description to Supermercado, then its tax is renamed`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado")

        val edited = this.success(this.edit(ahorros, mercado.id, "100000", description = "Supermercado"))

        assertEquals("4x1000 – Supermercado", edited.gmfTax!!.description)
    }

    @Test
    fun `given an expense without description and its tax, when describing it as Mercado, then the tax follows`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val expense = this.record(ahorros, "100000", "")

        val edited = this.success(this.edit(ahorros, expense.id, "100000", description = "Mercado"))

        assertEquals("4x1000", expense.gmfTax!!.description)
        assertEquals("4x1000 – Mercado", edited.gmfTax!!.description)
    }

    @Test
    fun `given Mercado with its tax, when clearing its description, then its tax is named 4x1000`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado")

        val edited = this.success(this.edit(ahorros, mercado.id, "100000", description = " "))

        assertEquals("4x1000", edited.gmfTax!!.description)
    }

    @Test
    fun `given Mercado taxed under GMF and Impuestos as category now, when editing it, then its tax keeps GMF`() {
        val gmfAhorros = this.ahorros("1000000", GmfTreatment.Taxed("cat-gmf", emptyList()))
        val mercado = this.record(gmfAhorros, "100000", "Mercado")
        val ahorros = this.restoreWith(gmfAhorros, this.impuestos, mercado)

        val edited = this.success(this.edit(ahorros, mercado.id, "50000"))

        assertEquals("cat-gmf", edited.gmfTax!!.categoryId)
    }

    @Test
    fun `given Ahorros at 0,60 after Mercado of 99601 and its tax, when editing Mercado to 100000, then a tax error`() {
        val ahorros = this.ahorros("100000", this.impuestos)
        val mercado = this.record(ahorros, "99601", "Mercado")

        val result = this.edit(ahorros, mercado.id, "100000")

        assertEquals("El monto más el 4x1000 supera el saldo disponible.", this.updateInvalidInput(result).amountError)
        assertEquals(this.pesos("0.60"), ahorros.balance)
    }

    @Test
    fun `given Ahorros at 0,60 after Mercado of 99601 and its tax, when editing Mercado to 99601, then it is saved`() {
        val ahorros = this.ahorros("100000", this.impuestos)
        val mercado = this.record(ahorros, "99601", "Mercado")

        val result = this.edit(ahorros, mercado.id, "99601")

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given Mercado recorded while exempt and Ahorros not exempt now, when editing it, then no tax is recorded`() {
        val exemptAhorros = this.ahorros("1000000", GmfTreatment.Exempt)
        val mercado = this.record(exemptAhorros, "100000", "Mercado")
        val ahorros = this.restoreWith(exemptAhorros, this.impuestos, mercado)

        val edited = this.success(this.edit(ahorros, mercado.id, "150000"))

        assertNull(edited.gmfTax)
        assertEquals(1, ahorros.expenses.size)
    }

    @Test
    fun `given Mercado with its tax of 400 and Ahorros exempt now, when editing it to 150000, then its tax is 600`() {
        val taxedAhorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(taxedAhorros, "100000", "Mercado")
        val ahorros = this.restoreWith(taxedAhorros, GmfTreatment.Exempt, mercado)

        val edited = this.success(this.edit(ahorros, mercado.id, "150000"))

        assertEquals(this.pesos("600.00"), edited.gmfTax!!.amount)
    }

    @Test
    fun `given Chicle of 1 recorded with no tax, when editing it to 100, then no tax is recorded`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val chicle = this.record(ahorros, "1.00", "Chicle")

        val edited = this.success(this.edit(ahorros, chicle.id, "100.00"))

        assertNull(edited.gmfTax)
    }

    @Test
    fun `given Chicle of 100 with its tax of 0,40, when editing it to 1, then its tax is removed`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val chicle = this.record(ahorros, "100.00", "Chicle")

        val edited = this.success(this.edit(ahorros, chicle.id, "1.00"))

        assertEquals(this.pesos("0.40"), chicle.gmfTax!!.amount)
        assertNull(edited.gmfTax)
        assertEquals(listOf(chicle.id), ahorros.expenses.map { it.id })
    }

    @Test
    fun `given Chicle whose tax was removed by an edit, when editing it back to 100, then no tax is recorded`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val chicle = this.record(ahorros, "100.00", "Chicle")
        this.edit(ahorros, chicle.id, "1.00")

        val edited = this.success(this.edit(ahorros, chicle.id, "100.00"))

        assertNull(edited.gmfTax)
    }

    @Test
    fun `given the id of a tax, when editing it, then fails with TransactionLookupError NotFound`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado")

        val result = this.edit(ahorros, mercado.gmfTax!!.id, "1000")

        assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
    }

    @Test
    fun `given Mercado of 100000 with its tax of 400, when deleting it, then both go and the balance grows 100400`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado")
        val balanceBefore = ahorros.balance.amount

        val result = ahorros.deleteExpense(mercado.id, this.clock)

        assertTrue(result is Outcome.Success)
        assertTrue(ahorros.expenses.isEmpty())
        assertEquals(0, BigDecimal("100400").compareTo(ahorros.balance.amount - balanceBefore))
    }

    @Test
    fun `given the id of a tax, when deleting it, then fails with TransactionLookupError NotFound`() {
        val ahorros = this.ahorros("1000000", this.impuestos)
        val mercado = this.record(ahorros, "100000", "Mercado")

        val result = ahorros.deleteExpense(mercado.gmfTax!!.id, this.clock)

        assertTrue((result as Outcome.Failure).error is TransactionLookupError.NotFound)
        assertEquals(2, ahorros.expenses.size)
    }

    @Test
    fun `given an income of 100400 paying Mercado and its tax, when lowering the income by one, then saldo error`() {
        val ahorros = this.ahorros("0", this.impuestos)
        val salario = (
            ahorros.createIncome("100400", "2026-10-01", "cat-salario", "", this.clock) as Outcome.Success
            ).value
        this.record(ahorros, "100000", "Mercado")

        val result = ahorros.editIncome(salario.id, "100399", "2026-10-01", "cat-salario", "", this.clock)

        val error = (result as Outcome.Failure).error as IncomeUpdateError.InvalidInput
        assertEquals("El saldo de la cuenta no puede quedar negativo.", error.amountError)
    }

    @Test
    fun `given an income paying the tax of a March 8 expense, when deleting the income, then a March 8 breach`() {
        val ahorros = this.ahorros("100000", this.impuestos, createdAt = "2026-03-01T12:00:00Z")
        val bono = (ahorros.createIncome("400", "2026-03-05", "cat-bono", "", this.clock) as Outcome.Success).value
        this.record(ahorros, "100000", "Mercado", date = "2026-03-08")

        val result = ahorros.deleteIncome(bono.id, this.clock)

        val breach = (result as Outcome.Failure).error as TransactionDeletionError.HistoryBreach
        assertEquals("El saldo de la cuenta quedaría negativo el 8 de marzo.", breach.externalMessage)
    }

    @Test
    fun `given Ahorros with 1000000, when recording an income of 500000, then only the income and 1500000`() {
        val ahorros = this.ahorros("1000000", this.impuestos)

        ahorros.createIncome("500000", "2026-10-22", "cat-salario", "", this.clock)

        assertTrue(ahorros.expenses.isEmpty())
        assertEquals(this.pesos("1500000"), ahorros.balance)
    }

    private fun ahorros(
        initialBalance: String,
        gmf: GmfTreatment,
        createdAt: String = "2026-01-01T12:00:00Z"
    ): Account = AccountBuilder()
        .id("ahorros")
        .name("Ahorros")
        .initialBalance(this.pesos(initialBalance))
        .gmf(gmf)
        .createdAt(Instant.parse(createdAt))
        .build()

    private fun restoreWith(account: Account, gmf: GmfTreatment, movement: Expense): Account = Account.restore(
        id = account.id,
        name = account.name,
        funding = AccountFunding.Funds(
            (account.funding as AccountFunding.Funds).initialBalance,
            MoneyAccountKind.SAVINGS,
            gmf
        ),
        description = account.description,
        createdAt = account.createdAt,
        expenses = listOf(movement)
    )

    private fun record(account: Account, amount: String, description: String, date: String = "2026-10-20"): Expense =
        (
            account.createExpense(
                amount = amount,
                date = date,
                categoryId = "cat-mercado",
                description = description,
                tagIds = emptyList(),
                chargeGmf = true,
                gmfTagIds = listOf("tag-4x1000"),
                clock = this.clock
            ) as Outcome.Success
            ).value

    private fun edit(
        account: Account,
        expenseId: String,
        amount: String,
        date: String = "2026-10-20",
        description: String = "Mercado"
    ): Outcome<Expense> = account.editExpense(
        expenseId = expenseId,
        amount = amount,
        date = date,
        categoryId = "cat-mercado",
        description = description,
        tagIds = emptyList(),
        clock = this.clock
    )

    private fun success(result: Outcome<Expense>): Expense = (result as Outcome.Success).value

    private fun updateInvalidInput(result: Outcome<Expense>): ExpenseUpdateError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertTrue(error is ExpenseUpdateError.InvalidInput)
        return error as ExpenseUpdateError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}
