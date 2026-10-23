package dev.raiseexception.odin.accounting.presentation.accountedit

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.presentation.TagSelection

sealed interface EditAccountUiState {
    data object Loading : EditAccountUiState
    data object NotFound : EditAccountUiState
    data object UnexpectedFailure : EditAccountUiState
    data class Editing(
        val name: String,
        val initialBalance: String,
        val currency: Currency?,
        val kind: MoneyAccountKind?,
        val description: String,
        val locked: Boolean,
        val lockedBalanceDisplay: String? = null,
        val nameError: String? = null,
        val balanceError: String? = null,
        val currencyError: String? = null,
        val typeError: String? = null,
        val descriptionError: String? = null,
        val isSaving: Boolean = false,
        val saveError: String? = null,
        val gmfExempt: Boolean = true,
        val gmfCategoryName: String = "",
        val gmfCategoryId: String = "",
        val categories: List<Category> = emptyList(),
        val tags: List<Tag> = emptyList(),
        val tagSelection: TagSelection = TagSelection(),
        val gmfCategoryError: String? = null
    ) : EditAccountUiState {
        val showsGmfOption: Boolean get() = this.kind == MoneyAccountKind.SAVINGS && this.currency == Currency.COP
    }
}
