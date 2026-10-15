package dev.raiseexception.odin.accounting.presentation.incomecreation

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.isMoneyAccount
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
class CreateIncomeViewModel(
    private val accountId: String?,
    private val incomeCreator: IncomeCreator,
    private val categoryLister: CategoryLister,
    private val accountFinder: AccountFinder,
    private val accountLister: AccountLister,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<CreateIncomeUiState>(CreateIncomeUiState.Loading)
    val uiState: StateFlow<CreateIncomeUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<NavigationTarget>(Channel.BUFFERED)
    val navigationEvent: Flow<NavigationTarget> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = CreateIncomeUiState.Loading
        this.restartableLoad.start()
    }

    fun save(amount: String, date: String, categoryInput: CategoryInput, description: String) {
        val stateBeforeSaving = this.mutableUiState.value
        if (stateBeforeSaving is CreateIncomeUiState.Saving) return
        val snapshot = this.currentSnapshot()
        val resolvedAccountId = this.accountId ?: snapshot.selectedAccountId
        if (resolvedAccountId == null) {
            this.mutableUiState.value = this.withAccountError(snapshot)
            return
        }
        this.mutableUiState.value = CreateIncomeUiState.Saving
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = {
                withContext(this.ioDispatcher) {
                    incomeCreator.create(
                        accountId = resolvedAccountId,
                        amount = amount,
                        date = date,
                        categoryInput = categoryInput,
                        description = description
                    )
                }
            },
            onResult = { outcome -> this.handleSave(outcome, snapshot) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(stateBeforeSaving) }
        )
    }

    fun onAccountSelected(selectedAccountId: String) {
        val current = this.mutableUiState.value
        when (current) {
            is CreateIncomeUiState.Idle -> this.mutableUiState.value = current.copy(
                selectedAccountId = selectedAccountId
            )
            is CreateIncomeUiState.ValidationError -> this.mutableUiState.value = current.copy(
                selectedAccountId = selectedAccountId,
                accountError = null
            )
            else -> Unit
        }
    }

    private suspend fun handleSave(outcome: Outcome<Income>, snapshot: FormSnapshot) {
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

    private fun load(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(loadedState()) }.flowOn(this.ioDispatcher),
            onEach = { loaded -> this.mutableUiState.value = loaded },
            onUnexpectedFailure = { this.mutableUiState.value = CreateIncomeUiState.UnexpectedFailure }
        )

    private suspend fun loadedState(): CreateIncomeUiState {
        val selectedAccountId = this.accountId
        return if (selectedAccountId != null) {
            this.loadWithAccount(selectedAccountId)
        } else {
            this.loadWithoutAccount()
        }
    }

    private suspend fun loadWithAccount(selectedAccountId: String): CreateIncomeUiState {
        val categoriesOutcome = this.categoryLister.list(CategoryType.INCOME, "").first()
        val accountOutcome = this.accountFinder.find(selectedAccountId).first()
        return when {
            categoriesOutcome is Outcome.Failure ->
                CreateIncomeUiState.Error(categoriesOutcome.error.externalMessage)
            accountOutcome is Outcome.Failure ->
                CreateIncomeUiState.Error(accountOutcome.error.externalMessage)
            else -> {
                val categories = (categoriesOutcome as Outcome.Success).value
                val account = (accountOutcome as Outcome.Success).value
                val createdAt = account.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                CreateIncomeUiState.Idle(categories = categories, accountCreatedAt = createdAt)
            }
        }
    }

    private suspend fun loadWithoutAccount(): CreateIncomeUiState {
        val categoriesOutcome = this.categoryLister.list(CategoryType.INCOME, "").first()
        val accountsOutcome = this.accountLister.list().first()
        return when {
            categoriesOutcome is Outcome.Failure ->
                CreateIncomeUiState.Error(categoriesOutcome.error.externalMessage)
            accountsOutcome is Outcome.Failure ->
                CreateIncomeUiState.Error(accountsOutcome.error.externalMessage)
            else -> {
                val categories = (categoriesOutcome as Outcome.Success).value
                val accounts = (accountsOutcome as Outcome.Success).value.filter { isMoneyAccount(it) }
                CreateIncomeUiState.Idle(
                    categories = categories,
                    accountCreatedAt = null,
                    accounts = accounts
                )
            }
        }
    }

    private fun mapError(error: DomainError, snapshot: FormSnapshot): CreateIncomeUiState {
        return when (error) {
            is IncomeCreationError.InvalidInput -> CreateIncomeUiState.ValidationError(
                categories = snapshot.categories,
                accountCreatedAt = snapshot.accountCreatedAt,
                accounts = snapshot.accounts,
                selectedAccountId = snapshot.selectedAccountId,
                amountError = error.amountError,
                dateError = error.dateError,
                categoryError = error.categoryError,
                descriptionError = error.descriptionError
            )
            else -> CreateIncomeUiState.Error(error.externalMessage)
        }
    }

    private fun withAccountError(snapshot: FormSnapshot): CreateIncomeUiState =
        CreateIncomeUiState.ValidationError(
            categories = snapshot.categories,
            accountCreatedAt = snapshot.accountCreatedAt,
            accounts = snapshot.accounts,
            selectedAccountId = snapshot.selectedAccountId,
            accountError = "La cuenta es obligatoria."
        )

    private fun currentSnapshot() = when (val current = this.mutableUiState.value) {
        is CreateIncomeUiState.Idle -> FormSnapshot(
            current.categories,
            current.accountCreatedAt,
            current.accounts,
            current.selectedAccountId,
        )
        is CreateIncomeUiState.ValidationError -> FormSnapshot(
            current.categories,
            current.accountCreatedAt,
            current.accounts,
            current.selectedAccountId,
        )
        else -> FormSnapshot(emptyList(), EPOCH_DATE, emptyList(), null)
    }

    private data class FormSnapshot(
        val categories: List<Category>,
        val accountCreatedAt: LocalDate?,
        val accounts: List<Account>,
        val selectedAccountId: String?,
    )

    companion object {
        private const val LOAD_OPERATION = "income-creation.load"
        private const val SUBMIT_OPERATION = "income-creation.submit"
        private val EPOCH_DATE = LocalDate(1970, 1, 1)
    }
}
