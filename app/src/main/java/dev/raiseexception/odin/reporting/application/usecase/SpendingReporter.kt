package dev.raiseexception.odin.reporting.application.usecase

import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryLister
import dev.raiseexception.odin.accounting.application.usecase.SpendingLister
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.SpanishAlphabeticalOrder
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.math.BigDecimal
import java.math.RoundingMode

private const val SHARE_SCALE = 10
private const val HUNDRED_PERCENT = 100
private val PERCENT_FACTOR = BigDecimal(HUNDRED_PERCENT)
private val currencyDisplayOrder = listOf(Currency.COP, Currency.USD, Currency.EUR)

class SpendingReporter(
    private val spendingLister: SpendingLister,
    private val categoryLister: CategoryLister,
    private val accountLister: AccountLister,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    fun startingPeriod(): ReportPeriod {
        val today = this.today()
        return ReportPeriod(LocalDate(today.year, today.monthNumber, 1), today)
    }

    fun validPeriod(start: LocalDate, end: LocalDate): ReportPeriod? =
        if (end < start || end > this.today()) null else ReportPeriod(start, end)

    fun report(period: ReportPeriod, chosenCurrency: Currency?): Flow<Outcome<SpendingReport>> =
        combine(
            this.spendingLister.list(period.start, period.end),
            this.categoryLister.list(CategoryType.EXPENSE, ""),
            this.accountLister.list(),
        ) { expensesOutcome, categoriesOutcome, accountsOutcome ->
            when {
                expensesOutcome is Outcome.Success &&
                    categoriesOutcome is Outcome.Success &&
                    accountsOutcome is Outcome.Success ->
                    this.summarize(
                        SpendingSources(expensesOutcome.value, categoriesOutcome.value, accountsOutcome.value),
                        period,
                        chosenCurrency,
                    )
                else -> listOf(expensesOutcome, categoriesOutcome, accountsOutcome)
                    .filterIsInstance<Outcome.Failure>()
                    .first()
            }
        }

    private fun today(): LocalDate = this.clock.todayIn(this.timeZone)

    private fun summarize(
        sources: SpendingSources,
        period: ReportPeriod,
        chosenCurrency: Currency?,
    ): Outcome<SpendingReport> {
        val categoriesById = sources.categories.associateBy { it.id }
        if (sources.expenses.any { it.categoryId !in categoriesById }) {
            return Outcome.Failure(StorageError("Expense category not found"))
        }
        val currencies = sources.accounts.map { it.currency }.distinct().sortedBy { currencyDisplayOrder.indexOf(it) }
        val currency = chosenCurrency ?: currencies.firstOrNull() ?: Currency.COP
        val totalsByCategory = sources.expenses
            .filter { it.amount.currency == currency }
            .groupBy { it.categoryId }
            .mapValues { (_, categoryExpenses) -> this.sum(categoryExpenses.map { it.amount.amount }) }
        val total = this.sum(totalsByCategory.values.toList())
        val categories = totalsByCategory
            .map { (categoryId, categoryTotal) ->
                this.categorySpending(categoriesById.getValue(categoryId), categoryTotal, total, currency)
            }
            .sortedWith(
                compareByDescending<CategorySpending> { it.total.amount }
                    .thenBy(SpanishAlphabeticalOrder.COMPARATOR) { it.name }
            )
        return Outcome.Success(
            SpendingReport(
                period = period,
                currencies = currencies,
                currency = currency,
                total = Money.of(total, currency),
                categories = categories,
            )
        )
    }

    private fun categorySpending(
        category: Category,
        categoryTotal: BigDecimal,
        total: BigDecimal,
        currency: Currency,
    ): CategorySpending {
        val percent = categoryTotal.multiply(PERCENT_FACTOR).divide(total, 0, RoundingMode.HALF_UP).toInt()
        return CategorySpending(
            categoryId = category.id,
            name = category.name,
            color = category.color,
            total = Money.of(categoryTotal, currency),
            share = categoryTotal.divide(total, SHARE_SCALE, RoundingMode.HALF_UP),
            displayedShare = if (percent == 0) DisplayedShare.BelowOnePercent else DisplayedShare.Whole(percent),
        )
    }

    private fun sum(amounts: List<BigDecimal>): BigDecimal =
        amounts.fold(BigDecimal.ZERO) { sum, amount -> sum.add(amount) }

    private data class SpendingSources(
        val expenses: List<Expense>,
        val categories: List<Category>,
        val accounts: List<Account>,
    )
}
