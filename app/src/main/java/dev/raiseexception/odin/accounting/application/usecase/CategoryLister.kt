package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryLister(private val categoryRepository: CategoryRepository) {

    fun list(filter: CategoryType?, name: String): Flow<List<Category>> =
        this.categoryRepository.getAll().map { this.filtered(it, filter, name) }

    private fun filtered(categories: List<Category>, filter: CategoryType?, name: String): List<Category> =
        categories
            .let { if (filter != null) it.filter { category -> category.type == filter } else it }
            .let { list ->
                if (name.isNotBlank()) list.filter { it.name.contains(name, ignoreCase = true) } else list
            }
}
