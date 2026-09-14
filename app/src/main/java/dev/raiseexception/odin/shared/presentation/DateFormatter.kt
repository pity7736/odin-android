package dev.raiseexception.odin.shared.presentation

import kotlinx.datetime.LocalDate

val SPANISH_MONTHS = arrayOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

fun formatFullSpanishDate(date: LocalDate): String =
    "${date.dayOfMonth} de ${SPANISH_MONTHS[date.monthNumber - 1]} de ${date.year}"
