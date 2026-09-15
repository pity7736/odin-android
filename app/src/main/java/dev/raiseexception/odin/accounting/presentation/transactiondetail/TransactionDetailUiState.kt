package dev.raiseexception.odin.accounting.presentation.transactiondetail

import androidx.compose.ui.graphics.Color

sealed interface TransactionDetailUiState {
    data object Loading : TransactionDetailUiState
    data class Content(
        val formattedAmount: String,
        val amountColor: Color,
        val formattedDate: String,
        val categoryName: String,
        val accountName: String,
        val description: String,
        val isIncome: Boolean,
    ) : TransactionDetailUiState
    data object NotFound : TransactionDetailUiState
    data class Error(val message: String) : TransactionDetailUiState
}
