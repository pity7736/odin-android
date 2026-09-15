package dev.raiseexception.odin.accounting.domain.model

data class TransactionDetail(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
)
