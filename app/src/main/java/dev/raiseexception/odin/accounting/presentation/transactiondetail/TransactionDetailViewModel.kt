package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.lifecycle.ViewModel
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.FailureRecorder
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.RestartableLoad
import dev.raiseexception.odin.shared.presentation.formatFullSpanishDate
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.shared.presentation.launchObservation
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeGreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class TransactionDetailViewModel(
    private val transactionId: String,
    private val transactionFinder: TransactionFinder,
    private val failureRecorder: FailureRecorder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Loading)
    val uiState: StateFlow<TransactionDetailUiState> = this.mutableUiState.asStateFlow()

    private val restartableLoad = RestartableLoad { this.observeTransaction() }

    init {
        this.restartableLoad.start()
    }

    fun retry() {
        this.mutableUiState.value = TransactionDetailUiState.Loading
        this.restartableLoad.start()
    }

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
        return TransactionDetailUiState.Content(
            formattedAmount = "$prefix${formatMoney(transaction.amount)}",
            amountColor = if (isIncome) IncomeGreen else ExpenseRed,
            formattedDate = formatFullSpanishDate(transaction.date),
            categoryName = detail.categoryName,
            accountName = detail.accountName,
            description = transaction.description,
            isIncome = isIncome,
            typeLabel = this.typeLabel(detail),
            isEditable = transaction is Expense && !detail.isTransfer,
            tagNames = detail.tags.map { it.name },
        )
    }

    private fun typeLabel(detail: TransactionDetail): String = when {
        detail.transaction !is Income -> "Gasto"
        detail.accountType == AccountType.CREDIT_CARD -> "Pago"
        else -> "Ingreso"
    }

    private companion object {
        const val LOAD_OPERATION = "transaction-detail.load"
    }
}
