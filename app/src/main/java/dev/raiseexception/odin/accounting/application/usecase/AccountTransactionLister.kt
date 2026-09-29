package dev.raiseexception.odin.accounting.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transaction
import dev.raiseexception.odin.accounting.domain.model.TransactionFilter

class AccountTransactionLister {

    fun list(account: Account, filter: TransactionFilter): List<AccountTransaction> {
        val filtered = this.applyFilter(account.transactions, filter)
        val sorted = this.sortByDateDescending(filtered)
        if (filter != TransactionFilter.ALL) {
            return sorted.map { AccountTransaction(it, null) }
        }
        return this.attachRunningBalances(sorted, account)
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
        account: Account
    ): List<AccountTransaction> {
        var runningBalance = account.balance.amount
        return sortedDescending.map { transaction ->
            val balanceAtThisPoint = Money.of(runningBalance, account.currency)
            val result = AccountTransaction(transaction, balanceAtThisPoint)
            runningBalance = runningBalance.subtract(account.funding.movementEffect(transaction))
            result
        }
    }
}
