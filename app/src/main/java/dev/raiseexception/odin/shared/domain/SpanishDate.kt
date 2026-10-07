package dev.raiseexception.odin.shared.domain

import kotlinx.datetime.LocalDate

val SPANISH_MONTHS = arrayOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

fun formatSpanishDayAndMonth(date: LocalDate, today: LocalDate): String {
    val dayAndMonth = "${date.dayOfMonth} de ${SPANISH_MONTHS[date.monthNumber - 1]}"
    return if (date.year == today.year) dayAndMonth else "$dayAndMonth de ${date.year}"
}
