package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

class CategoryFinder(private val categoryRepository: CategoryRepository) {

    fun find(id: String): Flow<Outcome<Category>> =
        this.categoryRepository.findById(id)
}
