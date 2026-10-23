package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.TransactionDeleter
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.UnexpectedFailureSignal
import dev.raiseexception.odin.shared.presentation.formatFullSpanishDate
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.shared.presentation.gmfTaxLineOf
import dev.raiseexception.odin.shared.presentation.launchObservation
import dev.raiseexception.odin.shared.presentation.launchOperation
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeGreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext

class TransactionDetailViewModel(
    private val transactionId: String,
    private val transactionFinder: TransactionFinder,
    private val transactionDeleter: TransactionDeleter,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Loading)
    val uiState: StateFlow<TransactionDetailUiState> = this.mutableUiState.asStateFlow()

    private val deletedChannel = Channel<String>(Channel.BUFFERED)
    val deletedEvent: Flow<String> = this.deletedChannel.receiveAsFlow()

    private val deletionMessageChannel = Channel<String>(Channel.BUFFERED)
    val deletionMessageEvent: Flow<String> = this.deletionMessageChannel.receiveAsFlow()

    private val unexpectedFailure = UnexpectedFailureSignal(this.mutableUiState)
    val unexpectedFailureEvent: Flow<Unit> = this.unexpectedFailure.event

    private val restartableLoad = RestartableLoad { this.observeTransaction() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = TransactionDetailUiState.Loading
        this.restartableLoad.start()
    }

    fun delete() {
        val current = this.mutableUiState.value
        if (current !is TransactionDetailUiState.Content || !current.isEditable || current.isDeleting) return
        this.mutableUiState.value = current.copy(isDeleting = true)
        this.restartableLoad.stop()
        this.launchOperation(
            operation = DELETE_OPERATION,
            recorder = this.failureRecorder,
            work = { withContext(this.ioDispatcher) { transactionDeleter.delete(transactionId) } },
            onResult = { outcome -> this.handleDeletion(current, outcome) },
            onUnexpectedFailure = {
                this.unexpectedFailure.restore(current)
                this.restartableLoad.start()
            }
        )
    }

    private suspend fun handleDeletion(content: TransactionDetailUiState.Content, outcome: Outcome<Unit>) {
        when (outcome) {
            is Outcome.Success -> this.deletedChannel.send(this.deletedNote(content))
            is Outcome.Failure -> {
                this.mutableUiState.value = content
                this.deletionMessageChannel.send(outcome.error.externalMessage)
                this.restartableLoad.start()
            }
        }
    }

    private fun deletedNote(content: TransactionDetailUiState.Content): String =
        if (content.isIncome) "Ingreso eliminado" else "Gasto eliminado"

    private fun observeTransaction(): Job =
        this.launchObservation(
            operation = LOAD_OPERATION,
            recorder = this.failureRecorder,
            flow = this.transactionFinder.find(this.transactionId)
                .map { outcome -> this.toUiState(outcome) }
                .flowOn(this.ioDispatcher),
            onEach = { state -> this.mutableUiState.value = state },
            onUnexpectedFailure = { this.mutableUiState.value = TransactionDetailUiState.UnexpectedFailure }
        )

    private fun toUiState(outcome: Outcome<TransactionDetail>): TransactionDetailUiState = when (outcome) {
        is Outcome.Success -> this.mapToContent(outcome)
        is Outcome.Failure -> when (outcome.error) {
            is TransactionLookupError.NotFound -> TransactionDetailUiState.NotFound
            else -> TransactionDetailUiState.Error(outcome.error.externalMessage)
        }
    }

    private fun mapToContent(
        outcome: Outcome.Success<TransactionDetail>
    ): TransactionDetailUiState.Content {
        val detail = outcome.value
        val transaction = detail.transaction
        val isIncome = transaction is Income
        val prefix = if (isIncome) "+" else "-"
        val gmfTax = (transaction as? Expense)?.gmfTax
        return TransactionDetailUiState.Content(
            formattedAmount = "$prefix${formatMoney(transaction.amount)}",
            amountColor = if (isIncome) IncomeGreen else ExpenseRed,
            formattedDate = formatFullSpanishDate(transaction.date),
            categoryName = detail.categoryName,
            accountName = detail.accountName,
            description = transaction.description,
            isIncome = isIncome,
            typeLabel = this.typeLabel(detail),
            isEditable = !detail.isTransfer && !detail.isGmfTax,
            tagNames = detail.tags.map { it.name },
            gmfTaxLine = gmfTax?.let { gmfTaxLineOf(it.amount.amount, it.amount.currency) },
            gmfTaxId = gmfTax?.id,
        )
    }

    private fun typeLabel(detail: TransactionDetail): String = when {
        detail.transaction !is Income -> "Gasto"
        detail.accountType == AccountType.CREDIT_CARD -> "Pago"
        else -> "Ingreso"
    }

    private companion object {
        const val LOAD_OPERATION = "transaction-detail.load"
        const val DELETE_OPERATION = "transaction-detail.delete"
    }
}
