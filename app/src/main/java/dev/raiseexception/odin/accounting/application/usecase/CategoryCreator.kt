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
        return when (val existsOutcome = this.categoryRepository.existsByNameAndType(category.name, category.type)) {
            is Outcome.Failure -> existsOutcome
            is Outcome.Success -> if (existsOutcome.value) {
                this.duplicateNameFailure(category.type)
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

    private fun duplicateNameFailure(type: CategoryType) = Outcome.Failure(
        CategoryCreationError.DuplicateName(
            internalMessage = "A category with the same name and type already exists",
            externalMessage = "Ya tienes una categoría de tipo ${typeLabel(type)} con ese nombre."
        )
    )

    private fun typeLabel(type: CategoryType): String = when (type) {
        CategoryType.INCOME -> "ingreso"
        CategoryType.EXPENSE -> "gasto"
    }
}
