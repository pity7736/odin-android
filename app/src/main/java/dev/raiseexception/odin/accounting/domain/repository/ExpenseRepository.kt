package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.shared.domain.Outcome

interface ExpenseRepository {
    suspend fun add(expense: Expense): Outcome<Unit>
}
