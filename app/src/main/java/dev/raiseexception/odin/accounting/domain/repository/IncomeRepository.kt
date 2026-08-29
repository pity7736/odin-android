package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.shared.domain.Outcome

interface IncomeRepository {
    suspend fun add(income: Income): Outcome<Unit>
}
