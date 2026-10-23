package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.shared.domain.Outcome

sealed interface GmfInput {
    data object Exempt : GmfInput
    data class Taxed(val categoryInput: CategoryInput, val tagNames: List<String>) : GmfInput
}

internal suspend fun GmfInput.resolveCategory(categoryResolver: CategoryResolver): Outcome<CategoryResolution?> =
    when (this) {
        is GmfInput.Exempt -> Outcome.Success(null)
        is GmfInput.Taxed -> if (this.categoryInput.isBlank()) {
            Outcome.Success(CategoryResolution.Rejected(null))
        } else {
            categoryResolver.resolve(this.categoryInput, CategoryType.EXPENSE)
        }
    }

internal fun GmfInput.treatment(category: CategoryResolution?): GmfTreatment = when (this) {
    is GmfInput.Exempt -> GmfTreatment.Exempt
    is GmfInput.Taxed -> GmfTreatment.Taxed(
        categoryId = (category as? CategoryResolution.Resolved)?.categoryId ?: "",
        tagNames = this.tagNames
    )
}

private fun CategoryInput.isBlank(): Boolean = when (this) {
    is CategoryInput.Existing -> this.categoryId.isBlank()
    is CategoryInput.New -> this.categoryName.isBlank()
}
