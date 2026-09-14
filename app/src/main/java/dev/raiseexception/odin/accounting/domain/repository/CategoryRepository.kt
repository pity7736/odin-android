package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    suspend fun existsByNameAndType(name: String, type: CategoryType): Outcome<Boolean>
    suspend fun add(category: Category): Outcome<Unit>
    fun findById(id: String): Flow<Outcome<Category>>
    fun getAll(): Flow<Outcome<List<Category>>>
}
