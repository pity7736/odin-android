package dev.raiseexception.odin.accounting.presentation.accountedit

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountUpdater
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.GmfInput
import dev.raiseexception.odin.accounting.application.usecase.TagLister
import dev.raiseexception.odin.accounting.domain.AccountUpdateError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TagInput
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.SelectedTag
import dev.raiseexception.odin.shared.presentation.TagSelection
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.amountInputToRaw
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.shared.presentation.launchObservation
import dev.raiseexception.odin.shared.presentation.launchOperation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")
class EditAccountViewModel(
    private val accountId: String,
    private val accountFinder: AccountFinder,
    private val accountUpdater: AccountUpdater,
    private val categoryLister: CategoryLister,
    private val tagLister: TagLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<EditAccountUiState>(EditAccountUiState.Loading)
    val uiState: StateFlow<EditAccountUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent: Flow<Unit> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = EditAccountUiState.Loading
        this.restartableLoad.start()
    }

    fun save(rawName: String, rawBalance: String, rawDescription: String, gmfCategoryInput: CategoryInput) {
        val current = this.mutableUiState.value
        if (current !is EditAccountUiState.Editing || current.isSaving) return
        val chargesGmf = current.showsGmfOption && !current.gmfExempt
        val tagSelection = if (chargesGmf) current.tagSelection.withTextAdded(current.tags) else current.tagSelection
        if (chargesGmf && tagSelection.error != null) {
            this.mutableUiState.value = current.copy(tagSelection = tagSelection)
            return
        }
        this.mutableUiState.value = current.copy(
            isSaving = true,
            nameError = null,
            balanceError = null,
            currencyError = null,
            typeError = null,
            descriptionError = null,
            saveError = null,
            gmfCategoryError = null,
            tagSelection = tagSelection
        )
        val gmf = if (chargesGmf) {
            GmfInput.Taxed(gmfCategoryInput, tagSelection.selected.map { it.name })
        } else {
            GmfInput.Exempt
        }
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = {
                withContext(this.ioDispatcher) {
                    accountUpdater.update(
                        accountId,
                        rawName,
                        amountInputToRaw(rawBalance),
                        current.currency,
                        current.kind,
                        rawDescription,
                        gmf
                    )
                }
            },
            onResult = { outcome -> this.handleSave(outcome) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(current) }
        )
    }

    fun onKindSelected(kind: MoneyAccountKind) {
        this.updateEditing { editing -> editing.copy(kind = kind) }
    }

    fun onCurrencySelected(currency: Currency) {
        this.updateEditing { editing -> editing.copy(currency = currency) }
    }

    fun onGmfExemptChanged(gmfExempt: Boolean) {
        this.updateEditing { editing -> editing.copy(gmfExempt = gmfExempt) }
    }

    fun onTagTextChange(text: String) {
        this.updateEditing { editing -> editing.copy(tagSelection = editing.tagSelection.withText(text)) }
    }

    fun addTag() {
        this.updateEditing { editing -> editing.copy(tagSelection = editing.tagSelection.withTextAdded(editing.tags)) }
    }

    fun pickTag(tag: Tag) {
        this.updateEditing { editing -> editing.copy(tagSelection = editing.tagSelection.withTagPicked(tag)) }
    }

    fun removeTag(index: Int) {
        this.updateEditing { editing -> editing.copy(tagSelection = editing.tagSelection.withTagRemoved(index)) }
    }

    private fun updateEditing(update: (EditAccountUiState.Editing) -> EditAccountUiState.Editing) {
        val current = this.mutableUiState.value as? EditAccountUiState.Editing ?: return
        this.mutableUiState.value = update(current)
    }

    private suspend fun handleSave(outcome: Outcome<Account>) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(Unit)
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(this.savingState(), outcome.error)
        }
    }

    private fun load(): Job {
        val criteria = AccountCriteria(includeIncomes = true, includeExpenses = true)
        return this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(loadedState(criteria)) }.flowOn(this.ioDispatcher),
            onEach = { loaded -> this.mutableUiState.value = loaded },
            onUnexpectedFailure = { this.mutableUiState.value = EditAccountUiState.UnexpectedFailure }
        )
    }

    private suspend fun loadedState(criteria: AccountCriteria): EditAccountUiState {
        val account = (this.accountFinder.find(this.accountId, criteria).first() as? Outcome.Success)?.value
            ?: return EditAccountUiState.NotFound
        val categories = (this.categoryLister.list(CategoryType.EXPENSE, "").first() as? Outcome.Success)?.value
            ?: return EditAccountUiState.NotFound
        val tags = (this.tagLister.list().first() as? Outcome.Success)?.value ?: return EditAccountUiState.NotFound
        return this.buildEditing(account, categories, tags)
    }

    private fun buildEditing(
        account: Account,
        categories: List<Category>,
        tags: List<Tag>
    ): EditAccountUiState.Editing {
        val locked = account.hasTransactions()
        val gmf = account.funding.gmfTreatment
        val gmfCategoryId = (gmf as? GmfTreatment.Taxed)?.categoryId ?: ""
        val gmfTagNames = (gmf as? GmfTreatment.Taxed)?.tagNames ?: emptyList()
        val (kind, initialBalance) = when (val funding = account.funding) {
            is AccountFunding.Funds -> Pair(funding.kind, funding.initialBalance)
            is AccountFunding.Credit -> Pair(null, funding.creditLimit)
        }
        return EditAccountUiState.Editing(
            name = account.name,
            initialBalance = initialBalance.amount.toPlainString(),
            currency = account.currency,
            kind = kind,
            description = account.description,
            locked = locked,
            lockedBalanceDisplay = if (locked) formatMoney(initialBalance) else null,
            gmfExempt = gmf !is GmfTreatment.Taxed,
            gmfCategoryName = categories.firstOrNull { it.id == gmfCategoryId }?.name ?: "",
            gmfCategoryId = gmfCategoryId,
            categories = categories,
            tags = tags,
            tagSelection = this.tagSelectionOf(gmfTagNames, tags)
        )
    }

    private fun tagSelectionOf(tagNames: List<String>, tags: List<Tag>): TagSelection = TagSelection(
        selected = tagNames.map { tagName ->
            tags.firstOrNull { it.normalizedName == Tag.normalize(tagName) }
                ?.let { SelectedTag(it.name, TagInput.Existing(it.id)) }
                ?: SelectedTag(tagName, TagInput.New(tagName))
        }
    )

    private fun savingState(): EditAccountUiState.Editing =
        this.mutableUiState.value as EditAccountUiState.Editing

    private fun mapError(editing: EditAccountUiState.Editing, error: DomainError): EditAccountUiState =
        when (error) {
            is AccountUpdateError.InvalidInput -> editing.copy(
                isSaving = false,
                nameError = error.nameError,
                balanceError = error.balanceError,
                currencyError = error.currencyError,
                typeError = error.typeError,
                descriptionError = error.descriptionError,
                gmfCategoryError = error.gmfCategoryError,
                tagSelection = editing.tagSelection.copy(error = error.gmfTagsError)
            )
            is AccountUpdateError.DuplicateName -> editing.copy(isSaving = false, nameError = error.externalMessage)
            is AccountUpdateError.StorageFailure -> editing.copy(isSaving = false, saveError = error.externalMessage)
            is AccountUpdateError.CreditCardNotEditable ->
                editing.copy(isSaving = false, saveError = error.externalMessage)
            else -> editing.copy(isSaving = false, saveError = error.externalMessage)
        }

    private companion object {
        const val LOAD_OPERATION = "account-edit.load"
        const val SUBMIT_OPERATION = "account-edit.submit"
    }
}
