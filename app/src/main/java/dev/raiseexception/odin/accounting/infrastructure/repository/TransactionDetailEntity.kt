package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.ColumnInfo
import androidx.room.Embedded
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.model.TransactionDetail

data class TransactionDetailEntity(
    @Embedded val transaction: TransactionEntity,
    @ColumnInfo(name = "categoryName") val categoryName: String,
    @ColumnInfo(name = "accountName") val accountName: String,
    @ColumnInfo(name = "isTransfer") val isTransfer: Boolean,
    @ColumnInfo(name = "isGmfTax") val isGmfTax: Boolean,
    @ColumnInfo(name = "accountType") val accountType: String,
)

internal fun TransactionDetailEntity.toDomain(tags: List<Tag>, gmfTax: Expense?): TransactionDetail {
    val domainTransaction = when (this.transaction.type) {
        "INCOME" -> this.transaction.toIncome()
        else -> this.transaction.toExpense(tags.map { it.id }, gmfTax)
    }
    return TransactionDetail(
        transaction = domainTransaction,
        categoryName = this.categoryName,
        accountName = this.accountName,
        isTransfer = this.isTransfer,
        isGmfTax = this.isGmfTax,
        accountType = AccountType.valueOf(this.accountType),
        tags = tags,
    )
}
