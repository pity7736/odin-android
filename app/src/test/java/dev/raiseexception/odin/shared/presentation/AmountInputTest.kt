package dev.raiseexception.odin.shared.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun `given digits when filtered then digits are kept`() {
        assertEquals("1500000", filterAmountInput("1500000"))
    }

    @Test
    fun `given a single comma when filtered then the comma is kept`() {
        assertEquals("111176,46", filterAmountInput("111176,46"))
    }

    @Test
    fun `given a second comma when filtered then the second comma is dropped`() {
        assertEquals("111176,46", filterAmountInput("111176,4,6"))
    }

    @Test
    fun `given a typed dot when filtered then the dot starts the decimal part`() {
        assertEquals("111,17", filterAmountInput("111.176"))
    }

    @Test
    fun `given a dot after digits when filtered then it is stored as the decimal comma`() {
        assertEquals("123,5", filterAmountInput("123.5"))
    }

    @Test
    fun `given a dot after a comma when filtered then the dot is dropped`() {
        assertEquals("123,5", filterAmountInput("123,5."))
    }

    @Test
    fun `given a comma after a dot when filtered then the comma is dropped`() {
        assertEquals("123,5", filterAmountInput("123.5,"))
    }

    @Test
    fun `given a second dot when filtered then the second dot is dropped`() {
        assertEquals("123,45", filterAmountInput("123.4.5"))
    }

    @Test
    fun `given a third decimal digit after a dot when filtered then it is dropped`() {
        assertEquals("1,46", filterAmountInput("1.467"))
    }

    @Test
    fun `given dot decimal and comma grouping when a comma is typed then it starts the decimal part`() {
        assertEquals(
            "123.5",
            filterAmountInput("123,5", decimalSeparator = '.', groupingSeparator = ','),
        )
    }

    @Test
    fun `given a dot typed decimal when converting to raw then the amount is correct`() {
        assertEquals("123.5", amountInputToRaw(filterAmountInput("123.5")))
    }

    @Test
    fun `given a sign or letter when filtered then it is dropped`() {
        assertEquals("111176", filterAmountInput("-111a176+"))
    }

    @Test
    fun `given a third decimal digit when filtered then it is dropped`() {
        assertEquals("111176,46", filterAmountInput("111176,467"))
    }

    @Test
    fun `given two decimal digits when filtered then they are kept`() {
        assertEquals("111176,46", filterAmountInput("111176,46"))
    }

    @Test
    fun `given an empty text when filtered then it stays empty`() {
        assertEquals("", filterAmountInput(""))
    }

    @Test
    fun `given a comma amount when converting to raw then the comma becomes a dot`() {
        assertEquals("111176.46", amountInputToRaw("111176,46"))
    }

    @Test
    fun `given a whole number when converting to raw then it is unchanged`() {
        assertEquals("1500000", amountInputToRaw("1500000"))
    }
}
