package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import dev.raiseexception.odin.accounting.domain.model.Expense

@Entity(
    tableName = "expense_tags",
    primaryKeys = ["expenseId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["expenseId"]),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"])
    ],
    indices = [Index("tagId")]
)
data class ExpenseTagEntity(
    val expenseId: String,
    val tagId: String
)

internal fun Expense.toExpenseTagEntities(): List<ExpenseTagEntity> =
    tagIds.map { tagId -> ExpenseTagEntity(expenseId = id, tagId = tagId) }
