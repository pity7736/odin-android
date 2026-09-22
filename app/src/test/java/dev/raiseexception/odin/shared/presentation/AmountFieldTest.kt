package dev.raiseexception.odin.shared.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFieldTest {

    @Test
    fun `given a value with comma typed, when normalized, then comma becomes dot`() {
        val result = normalizeAmountInput(",")
        assertEquals(".", result)
    }

    @Test
    fun `given a value with multiple commas, when normalized, then all commas become dots`() {
        val result = normalizeAmountInput("1,234,56")
        assertEquals("1.234.56", result)
    }

    @Test
    fun `given a value with no comma, when normalized, then value is unchanged`() {
        val result = normalizeAmountInput("12345")
        assertEquals("12345", result)
    }

    @Test
    fun `given an empty value, when normalized, then returns empty`() {
        val result = normalizeAmountInput("")
        assertEquals("", result)
    }
}
