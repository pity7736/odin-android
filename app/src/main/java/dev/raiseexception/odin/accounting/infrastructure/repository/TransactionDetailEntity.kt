package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.ColumnInfo
import androidx.room.Embedded
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail

data class TransactionDetailEntity(
    @Embedded val transaction: TransactionEntity,
    @ColumnInfo(name = "categoryName") val categoryName: String,
    @ColumnInfo(name = "accountName") val accountName: String,
    @ColumnInfo(name = "isTransfer") val isTransfer: Boolean,
)

internal fun TransactionDetailEntity.toDomain(): TransactionDetail {
    val domainTransaction = when (this.transaction.type) {
        "INCOME" -> this.transaction.toIncome()
        else -> this.transaction.toExpense()
    }
    return TransactionDetail(
        transaction = domainTransaction,
        categoryName = this.categoryName,
        accountName = this.accountName,
        isTransfer = this.isTransfer,
    )
}
