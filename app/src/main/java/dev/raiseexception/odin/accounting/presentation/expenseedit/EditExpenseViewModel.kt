package dev.raiseexception.odin.accounting.presentation.expenseedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseUpdater
import dev.raiseexception.odin.accounting.application.usecase.TagLister
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.ExpenseUpdateError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.TagSelection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Suppress("LongParameterList")
class EditExpenseViewModel(
    private val expenseId: String,
    private val transactionFinder: TransactionFinder,
    private val accountFinder: AccountFinder,
    private val categoryLister: CategoryLister,
    private val expenseUpdater: ExpenseUpdater,
    private val tagLister: TagLister,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<EditExpenseUiState>(EditExpenseUiState.Loading)
    val uiState: StateFlow<EditExpenseUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent: Flow<Unit> = this.navigationChannel.receiveAsFlow()

    init {
        this.load()
    }

    fun save(rawAmount: String, rawDate: String, categoryInput: CategoryInput, rawDescription: String) {
        val current = this.mutableUiState.value
        if (current !is EditExpenseUiState.Editing || current.isSaving) return
        val tagSelection = current.tagSelection.withTextAdded(current.tags)
        if (tagSelection.error != null) {
            this.mutableUiState.value = current.copy(tagSelection = tagSelection)
            return
        }
        this.mutableUiState.value = current.copy(
            isSaving = true,
            amountError = null,
            dateError = null,
            categoryError = null,
            descriptionError = null,
            saveError = null,
            tagSelection = tagSelection
        )
        this.viewModelScope.launch {
            val outcome = withContext(ioDispatcher) {
                expenseUpdater.update(expenseId, rawAmount, rawDate, categoryInput, rawDescription, tagSelection.inputs)
            }
            when (outcome) {
                is Outcome.Success -> navigationChannel.send(Unit)
                is Outcome.Failure -> mutableUiState.value = mapError(savingState(), outcome.error)
            }
        }
    }

    fun onTagTextChange(text: String) {
        this.updateTagSelection { selection, _ -> selection.withText(text) }
    }

    fun addTag() {
        this.updateTagSelection { selection, tags -> selection.withTextAdded(tags) }
    }

    fun pickTag(tag: Tag) {
        this.updateTagSelection { selection, _ -> selection.withTagPicked(tag) }
    }

    fun removeTag(index: Int) {
        this.updateTagSelection { selection, _ -> selection.withTagRemoved(index) }
    }

    private fun updateTagSelection(update: (TagSelection, List<Tag>) -> TagSelection) {
        val current = this.mutableUiState.value as? EditExpenseUiState.Editing ?: return
        this.mutableUiState.value = current.copy(tagSelection = update(current.tagSelection, current.tags))
    }

    private fun load() {
        this.viewModelScope.launch {
            mutableUiState.value = withContext(ioDispatcher) { loadEditing() } ?: EditExpenseUiState.NotFound
        }
    }

    private suspend fun loadEditing(): EditExpenseUiState.Editing? {
        val detail = (this.transactionFinder.find(this.expenseId).first() as? Outcome.Success)?.value
        val expense = detail?.transaction as? Expense
        if (expense == null || detail.isTransfer) return null
        val account = (this.accountFinder.find(expense.accountId).first() as? Outcome.Success)?.value ?: return null
        val categories = (this.categoryLister.list(CategoryType.EXPENSE, "").first() as? Outcome.Success)?.value
            ?: return null
        val tags = (this.tagLister.list().first() as? Outcome.Success)?.value ?: return null
        return EditExpenseUiState.Editing(
            amount = expense.amount.amount.toPlainString(),
            date = expense.date.toString(),
            categoryId = expense.categoryId,
            categoryName = detail.categoryName,
            description = expense.description,
            accountName = detail.accountName,
            accountCreatedAt = account.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
            categories = categories,
            tags = tags,
            tagSelection = TagSelection.of(detail.tags)
        )
    }

    private fun savingState(): EditExpenseUiState.Editing =
        this.mutableUiState.value as EditExpenseUiState.Editing

    private fun mapError(editing: EditExpenseUiState.Editing, error: DomainError): EditExpenseUiState =
        when (error) {
            is ExpenseUpdateError.InvalidInput -> editing.copy(
                isSaving = false,
                amountError = error.amountError,
                dateError = error.dateError,
                categoryError = error.categoryError,
                descriptionError = error.descriptionError,
                tagSelection = editing.tagSelection.copy(error = error.tagsError ?: editing.tagSelection.error)
            )
            else -> editing.copy(isSaving = false, saveError = error.externalMessage)
        }
}
