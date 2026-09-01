package dev.raiseexception.odin.accounting.infrastructure.serialization

import kotlinx.serialization.Serializable

@Serializable
data class ExpenseRecord(
    val recordType: String = EXPENSE_RECORD_TYPE,
    val id: String,
    val accountId: String,
    val amount: String,
    val currency: String,
    val date: String,
    val categoryId: String,
    val description: String,
    val createdAt: String
) {
    companion object {
        const val EXPENSE_RECORD_TYPE = "expense"
    }
}
