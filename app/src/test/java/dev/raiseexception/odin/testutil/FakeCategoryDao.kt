package dev.raiseexception.odin.testutil

import dev.raiseexception.odin.persistence.CategoryDao
import dev.raiseexception.odin.persistence.CategoryEntity

class FakeCategoryDao : CategoryDao {

    private val rows = mutableListOf<CategoryEntity>()

    override suspend fun insert(entity: CategoryEntity) {
        rows.add(entity)
    }

    override suspend fun getAll(): List<CategoryEntity> = rows.toList()
}
