package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class ThousandSeparatorTransformationTest {

    @Test
    fun `given digits with a decimal point, when formatted, then separates only the integer part and displays comma`() {
        val result = ThousandSeparatorTransformation.filter(AnnotatedString("1111764.46"))
        assertEquals("1.111.764,46", result.text.text)
    }

    @Test
    fun `given digits with trailing decimal point, when formatted, then keeps comma without extra dot`() {
        val result = ThousandSeparatorTransformation.filter(AnnotatedString("1111764."))
        assertEquals("1.111.764,", result.text.text)
    }

    @Test
    fun `given only integer digits, when formatted, then adds thousand separators`() {
        val result = ThousandSeparatorTransformation.filter(AnnotatedString("1234567"))
        assertEquals("1.234.567", result.text.text)
    }

    @Test
    fun `given a small number with decimals, when formatted, then no thousand separator needed`() {
        val result = ThousandSeparatorTransformation.filter(AnnotatedString("100.50"))
        assertEquals("100,50", result.text.text)
    }
}
