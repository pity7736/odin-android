package dev.raiseexception.odin.home.application.usecase

import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.Transaction
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal

const val DISPLAYED_ACCOUNT_LIMIT = 3

private const val MINIMUM_ACCOUNTS_FOR_TRANSFER = 2

private val currencyDisplayOrder = listOf(Currency.COP, Currency.USD, Currency.EUR)

class HomeSummaryLoader(
    private val accountLister: AccountLister,
    private val recentTransactionLister: RecentTransactionLister,
) {

    fun load(): Flow<Outcome<HomeSummary>> =
        this.accountLister.list(AccountCriteria(includeIncomes = true, includeExpenses = true)).map { outcome ->
            when (outcome) {
                is Outcome.Success -> Outcome.Success(this.summarize(outcome.value))
                is Outcome.Failure -> outcome
            }
        }

    private fun summarize(accounts: List<Account>): HomeSummary {
        val entries = this.mostRecentlyUsedFirst(accounts).map { this.toEntry(it) }
        val moneyAccountEntries = entries.filterIsInstance<HomeAccountEntry.MoneyAccountEntry>()
        val creditCardEntries = entries.filterIsInstance<HomeAccountEntry.CreditCardEntry>()
        return HomeSummary(
            balanceTotals = this.totalsByCurrency(moneyAccountEntries.map { it.balance }),
            debtTotals = this.totalsByCurrency(creditCardEntries.map { it.debt }),
            entries = entries.take(DISPLAYED_ACCOUNT_LIMIT),
            hasMoreEntries = entries.size > DISPLAYED_ACCOUNT_LIMIT,
            recentTransactions = this.recentTransactionLister.list(accounts),
            canTransfer = moneyAccountEntries.isNotEmpty() && entries.size >= MINIMUM_ACCOUNTS_FOR_TRANSFER,
            canRecordIncome = moneyAccountEntries.isNotEmpty(),
        )
    }

    private fun mostRecentlyUsedFirst(accounts: List<Account>): List<Account> {
        val (usedAccounts, unusedAccounts) = accounts.partition { it.hasTransactions() }
        val orderedUsedAccounts = usedAccounts.sortedWith(
            compareBy(mostRecentTransactionFirst) { this.mostRecentTransaction(it) }
        )
        return orderedUsedAccounts + unusedAccounts.sortedByDescending { it.createdAt }
    }

    private fun mostRecentTransaction(account: Account): Transaction =
        account.transactions.minWith(mostRecentTransactionFirst)

    private fun toEntry(account: Account): HomeAccountEntry = when (val funding = account.funding) {
        is AccountFunding.Funds -> HomeAccountEntry.MoneyAccountEntry(
            id = account.id,
            name = account.name,
            type = account.type,
            balance = account.balance,
        )
        is AccountFunding.Credit -> HomeAccountEntry.CreditCardEntry(
            id = account.id,
            name = account.name,
            debt = funding.currentDebt(account.incomes, account.expenses),
            availableCredit = funding.availableCredit(account.incomes, account.expenses),
        )
    }

    private fun totalsByCurrency(amounts: List<Money>): List<Money> =
        amounts
            .groupBy { it.currency }
            .map { (currency, currencyAmounts) ->
                val total = currencyAmounts.fold(BigDecimal.ZERO) { sum, money -> sum.add(money.amount) }
                Money.of(total, currency)
            }
            .sortedBy { currencyDisplayOrder.indexOf(it.currency) }
}
