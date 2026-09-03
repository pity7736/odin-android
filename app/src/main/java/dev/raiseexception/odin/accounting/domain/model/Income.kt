package dev.raiseexception.odin.accounting.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Suppress("LongParameterList")
class Income internal constructor(
    override val id: String,
    override val accountId: String,
    override val amount: Money,
    override val date: LocalDate,
    override val categoryId: String,
    override val description: String,
    override val createdAt: Instant
) : Transaction {

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
        ): Income = Income(
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
