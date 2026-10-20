package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.persistence.DatabaseProvider
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

class DeferredCategoryRepository(
    private val databaseProvider: DatabaseProvider
) : CategoryRepository {

    override suspend fun existsByNameAndType(name: String, type: CategoryType): Outcome<Boolean> =
        this.delegate().existsByNameAndType(name, type)

    override suspend fun add(category: Category): Outcome<Unit> = this.delegate().add(category)

    override fun findById(id: String): Flow<Outcome<Category>> = this.delegate().findById(id)

    override fun getAll(): Flow<Outcome<List<Category>>> = this.delegate().getAll()

    override fun findByType(type: CategoryType): Flow<Outcome<List<Category>>> = this.delegate().findByType(type)

    private fun delegate() = RoomCategoryRepository(this.databaseProvider.requireDatabase().categoryDao())
}
