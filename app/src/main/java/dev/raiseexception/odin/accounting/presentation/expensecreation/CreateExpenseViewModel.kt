package dev.raiseexception.odin.accounting.presentation.expensecreation

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.ExpenseCreator
import dev.raiseexception.odin.accounting.application.usecase.TagLister
import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.TagSelection
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Suppress("LongParameterList")
class CreateExpenseViewModel(
    private val accountId: String?,
    private val expenseCreator: ExpenseCreator,
    private val categoryLister: CategoryLister,
    private val accountFinder: AccountFinder,
    private val accountLister: AccountLister,
    private val tagLister: TagLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateExpenseUiState>(CreateExpenseUiState.Loading)
    val uiState: StateFlow<CreateExpenseUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = CreateExpenseUiState.Loading
        this.restartableLoad.start()
    }

    fun save(amount: String, date: String, categoryInput: CategoryInput, description: String) {
        val stateBeforeSaving = this.mutableUiState.value
        if (stateBeforeSaving is CreateExpenseUiState.Saving) return
        val snapshot = this.currentSnapshot().withTypedTagAdded()
        val resolvedAccountId = this.accountId ?: snapshot.selectedAccountId
        if (resolvedAccountId == null) {
            this.mutableUiState.value = this.validationError(snapshot, "La cuenta es obligatoria.")
            return
        }
        if (snapshot.tagSelection.error != null) {
            this.mutableUiState.value = this.validationError(snapshot, null)
            return
        }
        this.mutableUiState.value = CreateExpenseUiState.Saving
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = {
                withContext(this.ioDispatcher) {
                    expenseCreator.create(
                        accountId = resolvedAccountId,
                        amount = amount,
                        date = date,
                        categoryInput = categoryInput,
                        description = description,
                        tagInputs = snapshot.tagSelection.inputs
                    )
                }
            },
            onResult = { outcome -> this.handleSave(outcome, snapshot) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    private suspend fun handleSave(outcome: Outcome<Expense>, snapshot: FormSnapshot) {
        when (outcome) {
            is Outcome.Success -> {
                val target = if (this.accountId != null) {
                    NavigationTarget.AccountDetail(this.accountId)
                } else {
                    NavigationTarget.Back
                }
                this.navigationChannel.send(target)
            }
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(outcome.error, snapshot)
        }
    }

    fun onAccountSelected(selectedAccountId: String) {
        val current = this.mutableUiState.value
        when (current) {
            is CreateExpenseUiState.Idle -> this.mutableUiState.value = current.copy(
                selectedAccountId = selectedAccountId
            )
            is CreateExpenseUiState.ValidationError -> this.mutableUiState.value = current.copy(
                selectedAccountId = selectedAccountId,
                accountError = null
            )
            else -> Unit
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
        when (val current = this.mutableUiState.value) {
            is CreateExpenseUiState.Idle -> this.mutableUiState.value = current.copy(
                tagSelection = update(current.tagSelection, current.tags)
            )
            is CreateExpenseUiState.ValidationError -> this.mutableUiState.value = current.copy(
                tagSelection = update(current.tagSelection, current.tags)
            )
            else -> Unit
        }
    }

    private fun load(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(loadedState()) }.flowOn(this.ioDispatcher),
            onEach = { loaded -> this.mutableUiState.value = loaded },
            onUnexpectedFailure = { this.mutableUiState.value = CreateExpenseUiState.UnexpectedFailure }
        )

    private suspend fun loadedState(): CreateExpenseUiState {
        val selectedAccountId = this.accountId
        return if (selectedAccountId != null) {
            this.loadWithAccount(selectedAccountId)
        } else {
            this.loadWithoutAccount()
        }
    }

    private suspend fun loadWithAccount(selectedAccountId: String): CreateExpenseUiState {
        val categoriesOutcome = this.categoryLister.list(CategoryType.EXPENSE, "").first()
        val accountOutcome = this.accountFinder.find(selectedAccountId).first()
        val tagsOutcome = this.tagLister.list().first()
        return when {
            categoriesOutcome is Outcome.Failure ->
                CreateExpenseUiState.Error(categoriesOutcome.error.externalMessage)
            accountOutcome is Outcome.Failure ->
                CreateExpenseUiState.Error(accountOutcome.error.externalMessage)
            tagsOutcome is Outcome.Failure ->
                CreateExpenseUiState.Error(tagsOutcome.error.externalMessage)
            else -> {
                val categories = (categoriesOutcome as Outcome.Success).value
                val account = (accountOutcome as Outcome.Success).value
                val createdAt = account.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                CreateExpenseUiState.Idle(
                    categories = categories,
                    accountCreatedAt = createdAt,
                    tags = (tagsOutcome as Outcome.Success).value
                )
            }
        }
    }

    private suspend fun loadWithoutAccount(): CreateExpenseUiState {
        val categoriesOutcome = this.categoryLister.list(CategoryType.EXPENSE, "").first()
        val accountsOutcome = this.accountLister.list().first()
        val tagsOutcome = this.tagLister.list().first()
        return when {
            categoriesOutcome is Outcome.Failure ->
                CreateExpenseUiState.Error(categoriesOutcome.error.externalMessage)
            accountsOutcome is Outcome.Failure ->
                CreateExpenseUiState.Error(accountsOutcome.error.externalMessage)
            tagsOutcome is Outcome.Failure ->
                CreateExpenseUiState.Error(tagsOutcome.error.externalMessage)
            else -> {
                val categories = (categoriesOutcome as Outcome.Success).value
                val accounts = (accountsOutcome as Outcome.Success).value
                CreateExpenseUiState.Idle(
                    categories = categories,
                    accountCreatedAt = null,
                    accounts = accounts,
                    tags = (tagsOutcome as Outcome.Success).value
                )
            }
        }
    }

    private fun mapError(error: DomainError, snapshot: FormSnapshot): CreateExpenseUiState {
        return when (error) {
            is ExpenseCreationError.InvalidInput -> CreateExpenseUiState.ValidationError(
                categories = snapshot.categories,
                accountCreatedAt = snapshot.accountCreatedAt,
                accounts = snapshot.accounts,
                selectedAccountId = snapshot.selectedAccountId,
                amountError = error.amountError,
                dateError = error.dateError,
                categoryError = error.categoryError,
                descriptionError = error.descriptionError,
                tags = snapshot.tags,
                tagSelection = snapshot.tagSelection.copy(error = error.tagsError ?: snapshot.tagSelection.error)
            )
            else -> CreateExpenseUiState.Error(error.externalMessage)
        }
    }

    private fun validationError(snapshot: FormSnapshot, accountError: String?): CreateExpenseUiState =
        CreateExpenseUiState.ValidationError(
            categories = snapshot.categories,
            accountCreatedAt = snapshot.accountCreatedAt,
            accounts = snapshot.accounts,
            selectedAccountId = snapshot.selectedAccountId,
            accountError = accountError,
            tags = snapshot.tags,
            tagSelection = snapshot.tagSelection
        )

    private fun currentSnapshot() = when (val current = this.mutableUiState.value) {
        is CreateExpenseUiState.Idle -> FormSnapshot(
            current.categories,
            current.accountCreatedAt,
            current.accounts,
            current.selectedAccountId,
            current.tags,
            current.tagSelection,
        )
        is CreateExpenseUiState.ValidationError -> FormSnapshot(
            current.categories,
            current.accountCreatedAt,
            current.accounts,
            current.selectedAccountId,
            current.tags,
            current.tagSelection,
        )
        else -> FormSnapshot(emptyList(), EPOCH_DATE, emptyList(), null, emptyList(), TagSelection())
    }

    private data class FormSnapshot(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate?,
        val accounts: List<Account>,
        val selectedAccountId: String?,
        val tags: List<Tag>,
        val tagSelection: TagSelection,
    ) {
        fun withTypedTagAdded(): FormSnapshot = this.copy(tagSelection = this.tagSelection.withTextAdded(this.tags))
    }

    companion object {
        private const val LOAD_OPERATION = "expense-creation.load"
        private const val SUBMIT_OPERATION = "expense-creation.submit"
        private val EPOCH_DATE = LocalDate(1970, 1, 1)
    }
}
