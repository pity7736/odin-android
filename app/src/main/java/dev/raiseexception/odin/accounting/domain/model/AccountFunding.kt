package dev.raiseexception.odin.accounting.domain.model

import java.math.BigDecimal

sealed interface AccountFunding {
    val currency: Currency

    fun balance(incomes: List<Income>, expenses: List<Expense>): Money

    data class Funds(val initialBalance: Money) : AccountFunding {
        override val currency: Currency get() = this.initialBalance.currency

        override fun balance(incomes: List<Income>, expenses: List<Expense>): Money {
            val incomeSum = incomes.fold(BigDecimal.ZERO) { total, income -> total.add(income.amount.amount) }
            val expenseSum = expenses.fold(BigDecimal.ZERO) { total, expense -> total.add(expense.amount.amount) }
            return Money.of(this.initialBalance.amount.add(incomeSum).subtract(expenseSum), this.currency)
        }
    }

    data class Credit(val creditLimit: Money, val debt: Money) : AccountFunding {
        override val currency: Currency get() = this.creditLimit.currency

        val availableCredit: Money
            get() = Money.of(this.creditLimit.amount.subtract(this.debt.amount), this.currency)

        override fun balance(incomes: List<Income>, expenses: List<Expense>): Money = this.debt
    }
}
