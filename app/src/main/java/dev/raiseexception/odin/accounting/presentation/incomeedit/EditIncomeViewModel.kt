package dev.raiseexception.odin.accounting.presentation.incomeedit

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.AccountFinder
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.IncomeUpdater
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.IncomeUpdateError
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.shared.domain.DomainError
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Suppress("LongParameterList")
class EditIncomeViewModel(
    private val incomeId: String,
    private val transactionFinder: TransactionFinder,
    private val accountFinder: AccountFinder,
    private val categoryLister: CategoryLister,
    private val incomeUpdater: IncomeUpdater,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<EditIncomeUiState>(EditIncomeUiState.Loading)
    val uiState: StateFlow<EditIncomeUiState> = this.mutableUiState.asStateFlow()

    private val navigationChannel = Channel<Unit>(Channel.BUFFERED)
    val navigationEvent: Flow<Unit> = this.navigationChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private val restartableLoad = RestartableLoad { this.load() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = EditIncomeUiState.Loading
        this.restartableLoad.start()
    }

    fun save(rawAmount: String, rawDate: String, categoryInput: CategoryInput, rawDescription: String) {
        val current = this.mutableUiState.value
        if (current !is EditIncomeUiState.Editing || current.isSaving) return
        this.mutableUiState.value = current.copy(
            isSaving = true,
            amountError = null,
            dateError = null,
            categoryError = null,
            descriptionError = null,
            saveError = null
        )
        this.launchOperation(
            operation = SUBMIT_OPERATION,
            recorder = this.failureRecorder,
            work = {
                withContext(this.ioDispatcher) {
                    incomeUpdater.update(incomeId, rawAmount, rawDate, categoryInput, rawDescription)
                }
            },
            onResult = { outcome -> this.handleSave(outcome) },
            onUnexpectedFailure = { this.unexpectedFailure.restore(current) }
        )
    }

    private suspend fun handleSave(outcome: Outcome<Income>) {
        when (outcome) {
            is Outcome.Success -> this.navigationChannel.send(Unit)
            is Outcome.Failure -> this.mutableUiState.value = this.mapError(this.savingState(), outcome.error)
        }
    }

    private fun load(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = flow { emit(loadEditing()) }.flowOn(this.ioDispatcher),
            onEach = { editing -> this.mutableUiState.value = editing ?: EditIncomeUiState.NotFound },
            onUnexpectedFailure = { this.mutableUiState.value = EditIncomeUiState.UnexpectedFailure }
        )

    private suspend fun loadEditing(): EditIncomeUiState.Editing? {
        val detail = (this.transactionFinder.find(this.incomeId).first() as? Outcome.Success)?.value
        val income = detail?.transaction as? Income
        if (income == null || detail.isTransfer) return null
        val account = (this.accountFinder.find(income.accountId).first() as? Outcome.Success)?.value ?: return null
        val categories = (this.categoryLister.list(CategoryType.INCOME, "").first() as? Outcome.Success)?.value
            ?: return null
        return EditIncomeUiState.Editing(
            amount = income.amount.amount.toPlainString(),
            date = income.date.toString(),
            categoryId = income.categoryId,
            categoryName = detail.categoryName,
            description = income.description,
            accountName = detail.accountName,
            accountCreatedAt = account.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date,
            categories = categories
        )
    }

    private fun savingState(): EditIncomeUiState.Editing =
        this.mutableUiState.value as EditIncomeUiState.Editing

    private fun mapError(editing: EditIncomeUiState.Editing, error: DomainError): EditIncomeUiState =
        when (error) {
            is IncomeUpdateError.InvalidInput -> editing.copy(
                isSaving = false,
                amountError = error.amountError,
                dateError = error.dateError,
                categoryError = error.categoryError,
                descriptionError = error.descriptionError
            )
            else -> editing.copy(isSaving = false, saveError = error.externalMessage)
        }

    private companion object {
        const val LOAD_OPERATION = "income-edit.load"
        const val SUBMIT_OPERATION = "income-edit.submit"
    }
}
