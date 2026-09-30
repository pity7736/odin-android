package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.domain.model.Account

const val TRANSACTION_LIMIT = 5

class RecentTransactionLister {

    fun list(accounts: List<Account>, limit: Int = TRANSACTION_LIMIT): List<RecentTransaction> =
        accounts.flatMap { account ->
            account.transactions.map { transaction ->
                RecentTransaction(transaction = transaction, accountName = account.name)
            }
        }
            .sortedWith(compareBy(mostRecentTransactionFirst) { it.transaction })
            .take(limit)
}
