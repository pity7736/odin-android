package dev.raiseexception.odin.shared.domain

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishDateTest {

    private val today = LocalDate.parse("2026-10-06")

    @Test
    fun `given a date in the current year, when formatting, then returns day and month`() {
        assertEquals("5 de marzo", formatSpanishDayAndMonth(LocalDate.parse("2026-03-05"), this.today))
    }

    @Test
    fun `given a date in a previous year, when formatting, then adds the year`() {
        assertEquals("5 de diciembre de 2025", formatSpanishDayAndMonth(LocalDate.parse("2025-12-05"), this.today))
    }

    @Test
    fun `given a date in January, when formatting, then names the month enero`() {
        assertEquals("1 de enero", formatSpanishDayAndMonth(LocalDate.parse("2026-01-01"), this.today))
    }

    @Test
    fun `given a date in December, when formatting, then names the month diciembre`() {
        assertEquals("31 de diciembre de 2025", formatSpanishDayAndMonth(LocalDate.parse("2025-12-31"), this.today))
    }
}
