package dev.raiseexception.odin.accounting.domain.model

import org.junit.Assert.assertEquals
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
            debt = Money.of(BigDecimal("500000.00"), Currency.COP)
        )

        assertEquals(Currency.COP, funding.currency)
    }

    @Test
    fun `given credit funding, when reading its available credit, then returns the limit minus the debt`() {
        val funding = AccountFunding.Credit(
            creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
            debt = Money.of(BigDecimal("500000.00"), Currency.COP)
        )

        assertEquals(Money.of(BigDecimal("2500000.00"), Currency.COP), funding.availableCredit)
    }

    @Test
    fun `given credit funding, when computing its balance, then returns the debt`() {
        val funding = AccountFunding.Credit(
            creditLimit = Money.of(BigDecimal("3000000.00"), Currency.COP),
            debt = Money.of(BigDecimal("500000.00"), Currency.COP)
        )

        assertEquals(Money.of(BigDecimal("500000.00"), Currency.COP), funding.balance(emptyList(), emptyList()))
    }
}
