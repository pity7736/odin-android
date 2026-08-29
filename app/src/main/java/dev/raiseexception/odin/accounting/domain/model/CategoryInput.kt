package dev.raiseexception.odin.accounting.domain.model

sealed interface CategoryInput {
    data class Existing(val categoryId: String) : CategoryInput
    data class New(val categoryName: String) : CategoryInput
}
