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
}
