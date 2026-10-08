package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ExpenseRepository {
    suspend fun add(expense: Expense): Outcome<Unit>
    suspend fun update(expense: Expense): Outcome<Unit>
    fun findSpendingBetween(start: LocalDate, end: LocalDate): Flow<Outcome<List<Expense>>>
}
