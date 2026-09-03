package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transaction
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter

class AccountTransactionLister {

    fun list(
        transactions: List<Transaction>,
        currentBalance: Money,
        filter: TransactionFilter
    ): List<AccountTransaction> {
        val filtered = this.applyFilter(transactions, filter)
        val sorted = this.sortByDateDescending(filtered)
        if (filter != TransactionFilter.ALL) {
            return sorted.map { this.toAccountTransaction(it, null) }
        }
        return this.attachRunningBalances(sorted, currentBalance)
    }

    private fun applyFilter(
        transactions: List<Transaction>,
        filter: TransactionFilter
    ): List<Transaction> = when (filter) {
        TransactionFilter.ALL -> transactions
        TransactionFilter.INCOME -> transactions.filterIsInstance<Income>()
        TransactionFilter.EXPENSE -> transactions.filterIsInstance<Expense>()
    }

    private fun sortByDateDescending(transactions: List<Transaction>): List<Transaction> =
        transactions.sortedWith(
            compareByDescending<Transaction> { it.date }
                .thenByDescending { it.createdAt }
        )

    private fun attachRunningBalances(
        sortedDescending: List<Transaction>,
        currentBalance: Money
    ): List<AccountTransaction> {
        var runningBalance = currentBalance.amount
        return sortedDescending.map { transaction ->
            val balanceAtThisPoint = Money.of(runningBalance, currentBalance.currency)
            val result = this.toAccountTransaction(transaction, balanceAtThisPoint)
            runningBalance = if (transaction is Income) {
                runningBalance.subtract(transaction.amount.amount)
            } else {
                runningBalance.add(transaction.amount.amount)
            }
            result
        }
    }

    private fun toAccountTransaction(
        transaction: Transaction,
        runningBalance: Money?
    ): AccountTransaction =
        if (transaction is Income) {
            AccountTransaction.IncomeTransaction(
                id = transaction.id,
                amount = transaction.amount,
                date = transaction.date,
                categoryId = transaction.categoryId,
                description = transaction.description,
                runningBalance = runningBalance,
            )
        } else {
            AccountTransaction.ExpenseTransaction(
                id = transaction.id,
                amount = transaction.amount,
                date = transaction.date,
                categoryId = transaction.categoryId,
                description = transaction.description,
                runningBalance = runningBalance,
            )
        }
}
