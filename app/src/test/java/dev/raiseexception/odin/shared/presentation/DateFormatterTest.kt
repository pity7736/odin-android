package dev.raiseexception.odin.shared.presentation

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DateFormatterTest {

    @Test
    fun `given the first of October, when formatting short, then returns day, short month and year`() {
        assertEquals("1 oct 2026", formatShortSpanishDate(LocalDate.parse("2026-10-01")))
    }

    @Test
    fun `given the last of September, when formatting short, then returns day, short month and year`() {
        assertEquals("30 sep 2026", formatShortSpanishDate(LocalDate.parse("2026-09-30")))
    }
}
