package dev.raiseexception.odin.accounting.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `given an amount and a currency, when creating money, then holds both`() {
        val money = Money.of(BigDecimal("1500.00"), Currency.COP)

        assertEquals(0, money.amount.compareTo(BigDecimal("1500.00")))
        assertEquals(Currency.COP, money.currency)
    }

    @Test
    fun `given an amount with two decimals, when creating money, then succeeds`() {
        val money = Money.of(BigDecimal("10.25"), Currency.USD)

        assertEquals(0, money.amount.compareTo(BigDecimal("10.25")))
    }

    @Test
    fun `given an amount with more than two decimals, when creating money, then throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            Money.of(BigDecimal("10.255"), Currency.USD)
        }
    }

    @Test
    fun `given a negative amount, when creating money, then succeeds`() {
        val money = Money.of(BigDecimal("-5.00"), Currency.EUR)

        assertEquals(0, money.amount.compareTo(BigDecimal("-5.00")))
    }
}
