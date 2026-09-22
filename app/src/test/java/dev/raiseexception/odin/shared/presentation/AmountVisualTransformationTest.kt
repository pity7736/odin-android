package dev.raiseexception.odin.shared.presentation

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountVisualTransformationTest {

    private val transformation = AmountVisualTransformation()

    private fun format(text: String): String =
        transformation.filter(AnnotatedString(text)).text.text

    @Test
    fun `given a whole number when formatted then it is grouped with dots`() {
        assertEquals("1.500.000", format("1500000"))
    }

    @Test
    fun `given a comma amount when formatted then the whole part is grouped and the comma is kept`() {
        assertEquals("111.176,46", format("111176,46"))
    }

    @Test
    fun `given a short number when formatted then it has no separators`() {
        assertEquals("500", format("500"))
    }

    @Test
    fun `given an amount ending in the comma when formatted then the comma is kept`() {
        assertEquals("111.176,", format("111176,"))
    }

    @Test
    fun `given an empty text when formatted then it stays empty`() {
        assertEquals("", format(""))
    }

    @Test
    fun `given a comma amount when mapping every position then it round trips back to the original`() {
        val original = "111176,46"
        val transformed = transformation.filter(AnnotatedString(original))
        val mapping = transformed.offsetMapping
        for (offset in 0..original.length) {
            val roundTripped = mapping.transformedToOriginal(mapping.originalToTransformed(offset))
            assertEquals(offset, roundTripped)
        }
    }

    @Test
    fun `given a comma amount when mapping the ends then they map to the ends`() {
        val original = "111176,46"
        val transformed = transformation.filter(AnnotatedString(original))
        val mapping = transformed.offsetMapping
        assertEquals(0, mapping.originalToTransformed(0))
        assertEquals(transformed.text.text.length, mapping.originalToTransformed(original.length))
        assertEquals(original.length, mapping.transformedToOriginal(transformed.text.text.length))
    }
}
