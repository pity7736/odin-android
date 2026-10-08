package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.repository.ExpenseRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

class SpendingLister(private val expenseRepository: ExpenseRepository) {

    fun list(start: LocalDate, end: LocalDate): Flow<Outcome<List<Expense>>> =
        this.expenseRepository.findSpendingBetween(start, end)
}
