package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.raiseexception.odin.accounting.application.usecase.TransactionFinder
import dev.raiseexception.odin.accounting.domain.TransactionLookupError
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.presentation.formatFullSpanishDate
import dev.raiseexception.odin.shared.presentation.formatMoney
import dev.raiseexception.odin.ui.theme.ExpenseRed
import dev.raiseexception.odin.ui.theme.IncomeGreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class TransactionDetailViewModel(
    private val transactionId: String,
    private val transactionFinder: TransactionFinder,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Loading)
    val uiState: StateFlow<TransactionDetailUiState> = this.mutableUiState.asStateFlow()

    init {
        this.observeTransaction()
    }

    private fun observeTransaction() {
        this.viewModelScope.launch {
            this@TransactionDetailViewModel.transactionFinder.find(
                this@TransactionDetailViewModel.transactionId
            ).flowOn(this@TransactionDetailViewModel.ioDispatcher).collect { outcome ->
                this@TransactionDetailViewModel.mutableUiState.value = when (outcome) {
                    is Outcome.Success -> this@TransactionDetailViewModel.mapToContent(outcome)
                    is Outcome.Failure -> when (outcome.error) {
                        is TransactionLookupError.NotFound -> TransactionDetailUiState.NotFound
                        else -> TransactionDetailUiState.Error(outcome.error.externalMessage)
                    }
                }
            }
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
            isEditable = transaction is Expense && !detail.isTransfer,
        )
    }
}
