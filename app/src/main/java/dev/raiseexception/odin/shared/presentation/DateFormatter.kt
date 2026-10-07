package dev.raiseexception.odin.shared.presentation

import dev.raiseexception.odin.shared.domain.SPANISH_MONTHS
import kotlinx.datetime.LocalDate

fun formatFullSpanishDate(date: LocalDate): String =
    "${date.dayOfMonth} de ${SPANISH_MONTHS[date.monthNumber - 1]} de ${date.year}"
