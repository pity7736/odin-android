package dev.raiseexception.odin.accounting.domain.model

import dev.raiseexception.odin.accounting.domain.TransferCreationError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
            chargeGmf = true,
            gmfTagIds = emptyList(),
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
            chargeGmf = true,
            gmfTagIds = emptyList(),
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
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = fixedClock
        )
        val transfer = (result as Outcome.Success).value
        assertEquals("Transferencia desde Ahorros", transfer.income.description)
    }

    @Test
    fun `given successful transfer, when checking the expense side, then it has no tags`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = "200.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = fixedClock
        )
        val transfer = (result as Outcome.Success).value
        assertTrue(transfer.expense.tagIds.isEmpty())
    }

    @Test
    fun `given same source and destination, when creating transfer, then returns source account error`() {
        val result = Transfer.create(
            sourceAccount = sourceAccount,
            destinationAccount = sourceAccount,
            amount = "200.00",
            date = "2026-08-29",
            categoryId = "cat-transfer",
            chargeGmf = true,
            gmfTagIds = emptyList(),
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
            chargeGmf = true,
            gmfTagIds = emptyList(),
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
            chargeGmf = true,
            gmfTagIds = emptyList(),
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
            chargeGmf = true,
            gmfTagIds = emptyList(),
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
            chargeGmf = true,
            gmfTagIds = emptyList(),
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
            createdAt = fixedInstant,
            tagIds = emptyList(),
            gmfTax = null
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

    @Test
    fun `given a card as the source, when creating transfer, then rejects it and nothing is created`() {
        val visaCard = this.visaCard(initialDebt = "500.00")

        val result = this.transfer(source = visaCard, destination = this.destinationAccount, amount = "100.00")

        val error = this.invalidInput(result)
        assertEquals("Una tarjeta de crédito no puede ser la cuenta origen.", error.sourceAccountError)
        assertTrue(visaCard.expenses.isEmpty())
        assertTrue(this.destinationAccount.incomes.isEmpty())
    }

    @Test
    fun `given the same card as source and destination, when creating transfer, then reports the same account error`() {
        val visaCard = this.visaCard(initialDebt = "500.00")

        val result = this.transfer(source = visaCard, destination = visaCard, amount = "100.00")

        assertEquals("La cuenta origen y destino deben ser diferentes.", this.invalidInput(result).sourceAccountError)
    }

    @Test
    fun `given a card with debt 500, when paying 200 from a money account, then describes a payment and lowers debt`() {
        val visaCard = this.visaCard(initialDebt = "500.00")

        val result = this.transfer(source = this.sourceAccount, destination = visaCard, amount = "200.00")

        val transfer = (result as Outcome.Success).value
        assertEquals("Pago a Visa", transfer.expense.description)
        assertEquals("Pago desde Ahorros", transfer.income.description)
        assertEquals(Money.of(BigDecimal("300.00"), Currency.COP), visaCard.balance)
    }

    @Test
    fun `given a card with debt 500, when paying exactly 500, then succeeds and the debt is 0`() {
        val visaCard = this.visaCard(initialDebt = "500.00")

        val result = this.transfer(source = this.sourceAccount, destination = visaCard, amount = "500.00")

        assertTrue(result is Outcome.Success)
        assertEquals(Money.of(BigDecimal("0.00"), Currency.COP), visaCard.balance)
    }

    @Test
    fun `given a card with debt 500, when paying 600, then fails with the payment exceeds debt error`() {
        val visaCard = this.visaCard(initialDebt = "500.00")

        val result = this.transfer(source = this.sourceAccount, destination = visaCard, amount = "600.00")

        assertEquals("El pago no puede superar la deuda actual.", this.invalidInput(result).amountError)
    }

    @Test
    fun `given a card with no debt, when paying any amount, then fails with the payment exceeds debt error`() {
        val visaCard = this.visaCard(initialDebt = "0.00")

        val result = this.transfer(source = this.sourceAccount, destination = visaCard, amount = "1.00")

        assertEquals("El pago no puede superar la deuda actual.", this.invalidInput(result).amountError)
    }

    @Test
    fun `given a source with 1000, when paying 1500 to a card with debt 3000, then fails with insufficient funds`() {
        val visaCard = this.visaCard(initialDebt = "3000.00")

        val result = this.transfer(source = this.sourceAccount, destination = visaCard, amount = "1500.00")

        assertEquals("El monto supera el saldo disponible.", this.invalidInput(result).amountError)
    }

    @Test
    fun `given a card created on March 1, when paying it with a date of February 20, then fails with the date error`() {
        val earlySource = AccountBuilder()
            .id("src-early")
            .name("Ahorros")
            .initialBalance(Money.of(BigDecimal("1000.00"), Currency.COP))
            .createdAt(Instant.parse("2026-02-01T12:00:00Z"))
            .build()
        val visaCard = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
            .creditCard(this.pesos("3000.00"), this.pesos("500.00"))
            .build()

        val result = this.transfer(source = earlySource, destination = visaCard, amount = "100.00", date = "2026-02-20")

        assertEquals(
            "La fecha no puede ser anterior a la fecha de creación de la cuenta.",
            this.invalidInput(result).dateError
        )
    }

    @Test
    fun `given a card in another currency, when paying it, then fails with the same currency error`() {
        val dollarCard = AccountBuilder()
            .id("card-usd")
            .name("Visa")
            .creditCard(Money.of(BigDecimal("3000.00"), Currency.USD), Money.of(BigDecimal("500.00"), Currency.USD))
            .build()

        val result = this.transfer(source = this.sourceAccount, destination = dollarCard, amount = "100.00")

        assertEquals("Ambas cuentas deben usar la misma moneda.", this.invalidInput(result).destinationAccountError)
    }

    @Test
    fun `given a debt of 500 with 100 spent by September 10, when paying 300 that day, then fails with a date error`() {
        val visaCard = AccountBuilder()
            .id("card-1")
            .name("Visa")
            .creditCard(this.pesos("3000.00"), this.pesos("0.00"))
            .expenses(listOf(this.cardExpense("100.00", "2026-09-05"), this.cardExpense("400.00", "2026-09-20")))
            .build()
        val laterClock = object : Clock {
            override fun now(): Instant = Instant.parse("2026-09-25T12:00:00Z")
        }

        val result = Transfer.create(
            sourceAccount = this.sourceAccount,
            destinationAccount = visaCard,
            amount = "300.00",
            date = "2026-09-10",
            categoryId = "cat-transfer",
            chargeGmf = true,
            gmfTagIds = emptyList(),
            clock = laterClock
        )

        val error = this.invalidInput(result)
        assertEquals("La deuda de la tarjeta quedaría negativa el 10 de septiembre.", error.dateError)
        assertNull(error.amountError)
        assertTrue(visaCard.incomes.isEmpty())
    }

    @Test
    fun `given Ahorros at 0 with 200000 coming on March 10, when transferring it on March 5, then a date error`() {
        val savingsAccount = this.marchAccount(
            id = "src-1",
            name = "Ahorros",
            initialBalance = "0",
            incomes = listOf(this.marchIncome("src-1", "200000", "2026-03-10"))
        )
        val checkingAccount = this.marchAccount(id = "dst-1", name = "Corriente", initialBalance = "0")

        val result = this.transfer(
            source = savingsAccount,
            destination = checkingAccount,
            amount = "200000",
            date = "2026-03-05"
        )

        val error = this.invalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 5 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertTrue(savingsAccount.expenses.isEmpty())
        assertTrue(checkingAccount.incomes.isEmpty())
    }

    @Test
    fun `given a payment breaking Ahorros on March 8 and Visa on March 5, when paying, then the source error`() {
        val savingsAccount = this.marchAccount(
            id = "src-1",
            name = "Ahorros",
            initialBalance = "300000",
            incomes = listOf(this.marchIncome("src-1", "300000", "2026-03-15")),
            expenses = listOf(this.marchExpense("src-1", "250000", "2026-03-08"))
        )
        val visaCard = this.marchCard(
            expenses = listOf(
                this.marchExpense("card-1", "100000", "2026-03-03"),
                this.marchExpense("card-1", "400000", "2026-03-20")
            )
        )

        val result = this.transfer(
            source = savingsAccount,
            destination = visaCard,
            amount = "200000",
            date = "2026-03-05"
        )

        val error = this.invalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 8 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertEquals(1, savingsAccount.expenses.size)
        assertTrue(visaCard.incomes.isEmpty())
    }

    @Test
    fun `given Ahorros breaking on March 5 and a payment above the debt, when paying, then the source error`() {
        val savingsAccount = this.marchAccount(
            id = "src-1",
            name = "Ahorros",
            initialBalance = "0",
            incomes = listOf(this.marchIncome("src-1", "300000", "2026-03-10"))
        )
        val visaCard = this.marchCard(expenses = listOf(this.marchExpense("card-1", "100000", "2026-03-03")))

        val result = this.transfer(
            source = savingsAccount,
            destination = visaCard,
            amount = "200000",
            date = "2026-03-05"
        )

        val error = this.invalidInput(result)
        assertEquals("El saldo de la cuenta quedaría negativo el 5 de marzo.", error.dateError)
        assertNull(error.amountError)
        assertTrue(savingsAccount.expenses.isEmpty())
        assertTrue(visaCard.incomes.isEmpty())
    }

    private fun marchAccount(
        id: String,
        name: String,
        initialBalance: String,
        incomes: List<Income> = emptyList(),
        expenses: List<Expense> = emptyList()
    ): Account = AccountBuilder()
        .id(id)
        .name(name)
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .initialBalance(this.pesos(initialBalance))
        .incomes(incomes)
        .expenses(expenses)
        .build()

    private fun marchCard(expenses: List<Expense>): Account = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .createdAt(Instant.parse("2026-03-01T12:00:00Z"))
        .creditCard(this.pesos("1000000"), this.pesos("0"))
        .expenses(expenses)
        .build()

    private fun marchIncome(accountId: String, amount: String, date: String): Income = Income.restore(
        id = "income-$accountId-$date",
        accountId = accountId,
        amount = this.pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "cat-income",
        description = "",
        createdAt = Instant.parse("${date}T12:00:00Z")
    )

    private fun marchExpense(accountId: String, amount: String, date: String): Expense = Expense.restore(
        id = "expense-$accountId-$date",
        accountId = accountId,
        amount = this.pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "cat-1",
        description = "",
        createdAt = Instant.parse("${date}T12:00:00Z"),
        tagIds = emptyList(),
        gmfTax = null
    )

    private fun visaCard(initialDebt: String): Account = AccountBuilder()
        .id("card-1")
        .name("Visa")
        .creditCard(this.pesos("3000.00"), this.pesos(initialDebt))
        .build()

    private fun cardExpense(amount: String, date: String): Expense = Expense.restore(
        id = "expense-$date",
        accountId = "card-1",
        amount = this.pesos(amount),
        date = LocalDate.parse(date),
        categoryId = "cat-1",
        description = "",
        createdAt = Instant.parse("${date}T12:00:00Z"),
        tagIds = emptyList(),
        gmfTax = null
    )

    private fun transfer(
        source: Account,
        destination: Account,
        amount: String,
        date: String = "2026-08-29"
    ): Outcome<Transfer> = Transfer.create(
        sourceAccount = source,
        destinationAccount = destination,
        amount = amount,
        date = date,
        categoryId = "cat-transfer",
        chargeGmf = true,
        gmfTagIds = emptyList(),
        clock = this.fixedClock
    )

    private fun invalidInput(result: Outcome<Transfer>): TransferCreationError.InvalidInput {
        assertTrue(result is Outcome.Failure)
        return (result as Outcome.Failure).error as TransferCreationError.InvalidInput
    }

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}

class TransferGmfTest {

    private val fixedClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-08-29T12:00:00Z")
    }
    private val impuestos = GmfTreatment.Taxed(categoryId = "cat-impuestos", tagNames = listOf("4x1000"))

    @Test
    fun `given Ahorros with 1000000, when transferring 200000 to Corriente charging the tax, then a tax of 800`() {
        val ahorros = this.ahorros("1000000")
        val corriente = this.corriente()

        val transfer = (this.transfer(ahorros, corriente, "200000", chargeGmf = true) as Outcome.Success).value

        val tax = transfer.expense.gmfTax!!
        assertEquals("Transferencia a Corriente", transfer.expense.description)
        assertEquals("4x1000 – Transferencia a Corriente", tax.description)
        assertEquals(this.pesos("800.00"), tax.amount)
        assertEquals("cat-impuestos", tax.categoryId)
        assertEquals(listOf("tag-4x1000"), tax.tagIds)
        assertEquals(this.pesos("799200"), ahorros.balance)
        assertEquals(this.pesos("700000"), corriente.balance)
    }

    @Test
    fun `given Ahorros with 1000000, when transferring 200000 without charging the tax, then no tax is recorded`() {
        val ahorros = this.ahorros("1000000")

        val transfer = (this.transfer(ahorros, this.corriente(), "200000", chargeGmf = false) as Outcome.Success).value

        assertNull(transfer.expense.gmfTax)
        assertEquals(this.pesos("800000"), ahorros.balance)
    }

    @Test
    fun `given Visa with a debt of 500000, when paying it all from Ahorros charging the tax, then a tax of 2000`() {
        val ahorros = this.ahorros("1000000")
        val visa = AccountBuilder()
            .id("visa")
            .name("Visa")
            .creditCard(this.pesos("3000000"), this.pesos("500000"))
            .build()

        val transfer = (this.transfer(ahorros, visa, "500000", chargeGmf = true) as Outcome.Success).value

        assertEquals("4x1000 – Pago a Visa", transfer.expense.gmfTax!!.description)
        assertEquals(this.pesos("2000.00"), transfer.expense.gmfTax!!.amount)
        assertEquals(this.pesos("0"), visa.balance)
        assertEquals(this.pesos("498000"), ahorros.balance)
    }

    @Test
    fun `given Ahorros with 200000, when transferring 200000 charging the tax, then the amount plus tax error`() {
        val ahorros = this.ahorros("200000")

        val result = this.transfer(ahorros, this.corriente(), "200000", chargeGmf = true)

        val error = (result as Outcome.Failure).error as TransferCreationError.InvalidInput
        assertEquals("El monto más el 4x1000 supera el saldo disponible.", error.amountError)
    }

    @Test
    fun `given a non-exempt destination, when transferring to it, then the destination never pays a tax`() {
        val ahorros = this.ahorros("1000000")
        val taxedDestination = AccountBuilder()
            .id("otros-ahorros")
            .name("Otros ahorros")
            .initialBalance(this.pesos("0"))
            .gmf(this.impuestos)
            .build()

        this.transfer(ahorros, taxedDestination, "200000", chargeGmf = true)

        assertTrue(taxedDestination.expenses.isEmpty())
        assertEquals(this.pesos("200000"), taxedDestination.balance)
    }

    private fun ahorros(initialBalance: String): Account = AccountBuilder()
        .id("ahorros")
        .name("Ahorros")
        .initialBalance(this.pesos(initialBalance))
        .gmf(this.impuestos)
        .build()

    private fun corriente(): Account = AccountBuilder()
        .id("corriente")
        .name("Corriente")
        .initialBalance(this.pesos("500000"))
        .build()

    private fun transfer(
        source: Account,
        destination: Account,
        amount: String,
        chargeGmf: Boolean
    ): Outcome<Transfer> = Transfer.create(
        sourceAccount = source,
        destinationAccount = destination,
        amount = amount,
        date = "2026-08-29",
        categoryId = "cat-transfer",
        chargeGmf = chargeGmf,
        gmfTagIds = listOf("tag-4x1000"),
        clock = this.fixedClock
    )

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}
