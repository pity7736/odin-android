package dev.raiseexception.odin.accounting.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

interface Transaction {
    val id: String
    val accountId: String
    val amount: Money
    val date: LocalDate
    val categoryId: String
    val description: String
    val createdAt: Instant
}
