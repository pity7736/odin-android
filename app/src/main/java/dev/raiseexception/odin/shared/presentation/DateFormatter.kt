package dev.raiseexception.odin.shared.presentation

import dev.raiseexception.odin.shared.domain.SPANISH_MONTHS
import kotlinx.datetime.LocalDate

private const val SHORT_MONTH_LENGTH = 3

fun formatFullSpanishDate(date: LocalDate): String =
    "${date.dayOfMonth} de ${SPANISH_MONTHS[date.monthNumber - 1]} de ${date.year}"

fun formatShortSpanishDate(date: LocalDate): String =
    "${date.dayOfMonth} ${SPANISH_MONTHS[date.monthNumber - 1].take(SHORT_MONTH_LENGTH)} ${date.year}"
