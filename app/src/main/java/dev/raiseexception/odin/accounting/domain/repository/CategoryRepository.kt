package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.Outcome

interface CategoryRepository {
    suspend fun existsByNameAndType(name: String, type: CategoryType): Outcome<Boolean>
    suspend fun add(category: Category): Outcome<Unit>
}
