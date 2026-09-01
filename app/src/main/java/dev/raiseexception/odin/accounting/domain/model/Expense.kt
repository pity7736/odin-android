package dev.raiseexception.odin.accounting.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Suppress("LongParameterList")
class Expense internal constructor(
    val id: String,
    val accountId: String,
    val amount: Money,
    val date: LocalDate,
    val categoryId: String,
    val description: String,
    val createdAt: Instant
) {

    companion object {
        @Suppress("LongParameterList")
        fun restore(
            id: String,
            accountId: String,
            amount: Money,
            date: LocalDate,
            categoryId: String,
            description: String,
            createdAt: Instant
        ): Expense = Expense(
            id = id,
            accountId = accountId,
            amount = amount,
            date = date,
            categoryId = categoryId,
            description = description,
            createdAt = createdAt
        )
    }
}
