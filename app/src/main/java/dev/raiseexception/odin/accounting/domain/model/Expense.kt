package dev.raiseexception.odin.accounting.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Suppress("LongParameterList")
class Expense internal constructor(
    override val id: String,
    override val accountId: String,
    override val amount: Money,
    override val date: LocalDate,
    override val categoryId: String,
    override val description: String,
    override val createdAt: Instant,
    val tagIds: List<String>
) : Transaction {

    companion object {
        const val MAX_TAGS = 5
        const val TAG_LIMIT_MESSAGE = "Máximo 5 etiquetas por gasto."

        @Suppress("LongParameterList")
        fun restore(
            id: String,
            accountId: String,
            amount: Money,
            date: LocalDate,
            categoryId: String,
            description: String,
            createdAt: Instant,
            tagIds: List<String>
        ): Expense = Expense(
            id = id,
            accountId = accountId,
            amount = amount,
            date = date,
            categoryId = categoryId,
            description = description,
            createdAt = createdAt,
            tagIds = tagIds
        )

        internal fun validateTagCount(uniqueTagIds: List<String>): String? =
            if (uniqueTagIds.size > MAX_TAGS) TAG_LIMIT_MESSAGE else null
    }
}
