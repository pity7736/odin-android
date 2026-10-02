package dev.raiseexception.odin.accounting.presentation.expenseedit

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.presentation.TagSelection
import kotlinx.datetime.LocalDate

sealed interface EditExpenseUiState {
    data object Loading : EditExpenseUiState
    data object NotFound : EditExpenseUiState
    data class Editing(
        val amount: String,
        val date: String,
        val categoryId: String,
        val categoryName: String,
        val description: String,
        val accountName: String,
        val accountCreatedAt: LocalDate,
        val categories: List<Category>,
        val amountError: String? = null,
        val dateError: String? = null,
        val categoryError: String? = null,
        val descriptionError: String? = null,
        val isSaving: Boolean = false,
        val saveError: String? = null,
        val tags: List<Tag> = emptyList(),
        val tagSelection: TagSelection = TagSelection()
    ) : EditExpenseUiState
}
