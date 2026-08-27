package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome

class CategoryCreator(
    private val categoryRepository: CategoryRepository,
    private val colorPicker: () -> String = { Category.DEFAULT_PALETTE.random() }
) {

    suspend fun create(
        name: String,
        type: CategoryType?,
        description: String,
        color: String?
    ): Outcome<Category> {
        val resolvedColor = color ?: this.colorPicker()
        val category = when (val creationOutcome = Category.create(name, type, description, resolvedColor)) {
            is Outcome.Success -> creationOutcome.value
            is Outcome.Failure -> return creationOutcome
        }
        return when (val existsOutcome = this.categoryRepository.existsByName(category.name)) {
            is Outcome.Failure -> existsOutcome
            is Outcome.Success -> if (existsOutcome.value) {
                this.duplicateNameFailure()
            } else {
                this.persist(category)
            }
        }
    }

    private suspend fun persist(category: Category): Outcome<Category> =
        when (val addOutcome = this.categoryRepository.add(category)) {
            is Outcome.Failure -> addOutcome
            is Outcome.Success -> Outcome.Success(category)
        }

    private fun duplicateNameFailure() = Outcome.Failure(
        CategoryCreationError.DuplicateName(
            internalMessage = "A category with the same name already exists",
            externalMessage = "Ya tienes una categoría con ese nombre."
        )
    )
}
