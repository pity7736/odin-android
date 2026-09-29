package dev.raiseexception.odin.accounting.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class AccountFundingTest {

    @Test
    fun `given funds funding, when reading its currency, then returns the initial balance currency`() {
        val funding = AccountFunding.Funds(Money.of(BigDecimal("1000.00"), Currency.COP))

        assertEquals(Currency.COP, funding.currency)
    }

    @Test
    fun `given funds funding, when computing its balance, then returns the initial balance`() {
        val funding = AccountFunding.Funds(Money.of(BigDecimal("1000.00"), Currency.COP))

        assertEquals(Money.of(BigDecimal("1000.00"), Currency.COP), funding.balance(emptyList(), emptyList()))
    }

    @Test
    fun `given credit funding, when reading its currency, then returns the credit limit currency`() {
        val funding = AccountFunding.Credit(
            creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
            initialDebt = Money.of(BigDecimal("500000.00"), Currency.COP)
        )

        assertEquals(Currency.COP, funding.currency)
    }

    @Test
    fun `given a 3000000 card with debt 500000, when it has no expenses, then debt 500000 and available 2500000`() {
        val funding = this.visaCredit()

        assertEquals(this.pesos("500000.00"), funding.currentDebt(emptyList(), emptyList()))
        assertEquals(this.pesos("2500000.00"), funding.availableCredit(emptyList(), emptyList()))
    }

    @Test
    fun `given that card, when it has expenses of 200000 and 100000, then debt 800000 and available 2200000`() {
        val funding = this.visaCredit()
        val expenses = listOf(this.expense("200000"), this.expense("100000"))

        assertEquals(this.pesos("800000.00"), funding.currentDebt(emptyList(), expenses))
        assertEquals(this.pesos("2200000.00"), funding.availableCredit(emptyList(), expenses))
    }

    @Test
    fun `given a card, when its expenses bring the debt to its limit, then available credit is 0`() {
        val funding = this.visaCredit()
        val expenses = listOf(this.expense("2500000"))

        assertEquals(this.pesos("0.00"), funding.availableCredit(emptyList(), expenses))
    }

    @Test
    fun `given a card with initial debt 100 expenses 300 and payments 150, when computing its debt, then 250`() {
        val funding = this.smallCredit()

        assertEquals(this.pesos("250.00"), funding.currentDebt(this.smallPayments(), this.smallExpenses()))
    }

    @Test
    fun `given a 1000 card with those movements, when computing its available credit, then 750`() {
        val funding = this.smallCredit()

        assertEquals(this.pesos("750.00"), funding.availableCredit(this.smallPayments(), this.smallExpenses()))
    }

    @Test
    fun `given a card with payments, when computing its balance, then equals its debt net of payments`() {
        val funding = this.smallCredit()

        assertEquals(this.pesos("250.00"), funding.balance(this.smallPayments(), this.smallExpenses()))
    }

    @Test
    fun `given a card with payments, when asking what can be spent, then available credit net of payments`() {
        val funding = this.smallCredit()

        assertEquals(this.pesos("750.00"), funding.spendable(this.smallPayments(), this.smallExpenses()))
    }

    @Test
    fun `given a card, when asking what can be spent, then available credit and a cupo message`() {
        val funding = this.visaCredit()
        val expenses = listOf(this.expense("200000"))

        assertEquals(funding.availableCredit(emptyList(), expenses), funding.spendable(emptyList(), expenses))
        assertEquals("El monto supera el cupo disponible.", funding.overSpendMessage)
    }

    @Test
    fun `given a card with debt 250, when a payment below the debt comes in, then no error`() {
        val funding = this.smallCredit()

        assertNull(funding.validateIncomingAmount(BigDecimal("200"), this.smallPayments(), this.smallExpenses()))
    }

    @Test
    fun `given a card with debt 250, when a payment equal to the debt comes in, then no error`() {
        val funding = this.smallCredit()

        assertNull(funding.validateIncomingAmount(BigDecimal("250"), this.smallPayments(), this.smallExpenses()))
    }

    @Test
    fun `given a card with debt 250, when a payment above the debt comes in, then the payment exceeds debt message`() {
        val funding = this.smallCredit()

        assertEquals(
            "El pago no puede superar la deuda actual.",
            funding.validateIncomingAmount(BigDecimal("251"), this.smallPayments(), this.smallExpenses())
        )
    }

    @Test
    fun `given a card with no debt, when any positive payment comes in, then the payment exceeds debt message`() {
        val funding = AccountFunding.Credit(creditLimit = this.pesos("1000.00"), initialDebt = this.pesos("0.00"))

        assertEquals(
            "El pago no puede superar la deuda actual.",
            funding.validateIncomingAmount(BigDecimal("1"), emptyList(), emptyList())
        )
    }

    @Test
    fun `given a money account, when any amount comes in, then no error`() {
        val funding = AccountFunding.Funds(this.pesos("0.00"))

        assertNull(funding.validateIncomingAmount(BigDecimal("999999999"), emptyList(), emptyList()))
    }

    @Test
    fun `given a money account, when asking what can be spent, then balance and a saldo message`() {
        val funding = AccountFunding.Funds(this.pesos("1000000.00"))
        val expenses = listOf(this.expense("200000"))
        val incomes = listOf(this.income("50000"))

        assertEquals(funding.balance(incomes, expenses), funding.spendable(incomes, expenses))
        assertEquals("El monto supera el saldo disponible.", funding.overSpendMessage)
    }

    private fun smallCredit(): AccountFunding.Credit = AccountFunding.Credit(
        creditLimit = this.pesos("1000.00"),
        initialDebt = this.pesos("100.00")
    )

    private fun smallExpenses(): List<Expense> = listOf(this.expense("300"))

    private fun smallPayments(): List<Income> = listOf(this.income("150"))

    private fun visaCredit(): AccountFunding.Credit = AccountFunding.Credit(
        creditLimit = this.pesos("3000000.00"),
        initialDebt = this.pesos("500000.00")
    )

    private fun expense(amount: String): Expense = Expense.restore(
        id = "expense-$amount",
        accountId = "card-1",
        amount = this.pesos(amount),
        date = LocalDate.parse("2026-03-10"),
        categoryId = "cat-1",
        description = "",
        createdAt = Instant.parse("2026-03-10T12:00:00Z")
    )

    private fun income(amount: String): Income = Income.restore(
        id = "income-$amount",
        accountId = "card-1",
        amount = this.pesos(amount),
        date = LocalDate.parse("2026-03-10"),
        categoryId = "cat-2",
        description = "",
        createdAt = Instant.parse("2026-03-10T12:00:00Z")
    )

    private fun pesos(amount: String): Money = Money.of(BigDecimal(amount), Currency.COP)
}
