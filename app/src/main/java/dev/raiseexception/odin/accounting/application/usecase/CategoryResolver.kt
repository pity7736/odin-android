package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.first

class CategoryResolver(
    private val categoryRepository: CategoryRepository,
    private val categoryCreator: CategoryCreator
) {

    suspend fun resolve(categoryInput: CategoryInput, type: CategoryType): Outcome<CategoryResolution> =
        when (categoryInput) {
            is CategoryInput.Existing -> this.resolveExisting(categoryInput.categoryId, type)
            is CategoryInput.New -> this.resolveNew(categoryInput.categoryName, type)
        }

    private suspend fun resolveExisting(categoryId: String, type: CategoryType): Outcome<CategoryResolution> =
        when (val lookupOutcome = this.categoryRepository.findById(categoryId).first()) {
            is Outcome.Failure -> if (lookupOutcome.error is CategoryLookupError.NotFound) {
                Outcome.Success(CategoryResolution.Rejected("La categoría seleccionada no existe."))
            } else {
                lookupOutcome
            }
            is Outcome.Success -> if (lookupOutcome.value.type == type) {
                Outcome.Success(CategoryResolution.Resolved(lookupOutcome.value.id))
            } else {
                Outcome.Success(CategoryResolution.Rejected(this.wrongTypeMessage(type)))
            }
        }

    private suspend fun resolveNew(categoryName: String, type: CategoryType): Outcome<CategoryResolution> =
        when (val creationOutcome = this.categoryCreator.create(categoryName, type, "", null)) {
            is Outcome.Success -> Outcome.Success(CategoryResolution.Resolved(creationOutcome.value.id))
            is Outcome.Failure -> when (val error = creationOutcome.error) {
                is CategoryCreationError.InvalidInput -> Outcome.Success(CategoryResolution.Rejected(error.nameError))
                is CategoryCreationError.DuplicateName ->
                    Outcome.Success(CategoryResolution.Rejected(error.externalMessage))
                else -> creationOutcome
            }
        }

    private fun wrongTypeMessage(type: CategoryType): String = when (type) {
        CategoryType.INCOME -> "La categoría seleccionada no es de tipo ingreso."
        CategoryType.EXPENSE -> "La categoría seleccionada no es de tipo gasto."
        CategoryType.TRANSFER -> "La categoría seleccionada no es de tipo transferencia."
    }
}

sealed interface CategoryResolution {
    data class Resolved(val categoryId: String) : CategoryResolution
    data class Rejected(val categoryError: String?) : CategoryResolution
}
