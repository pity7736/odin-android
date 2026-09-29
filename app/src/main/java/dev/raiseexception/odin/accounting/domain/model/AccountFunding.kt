package dev.raiseexception.odin.accounting.domain.model

import java.math.BigDecimal

sealed interface AccountFunding {
    val currency: Currency

    val overSpendMessage: String

    fun balance(incomes: List<Income>, expenses: List<Expense>): Money

    fun spendable(incomes: List<Income>, expenses: List<Expense>): Money

    data class Funds(val initialBalance: Money) : AccountFunding {
        override val currency: Currency get() = this.initialBalance.currency

        override val overSpendMessage: String get() = "El monto supera el saldo disponible."

        override fun balance(incomes: List<Income>, expenses: List<Expense>): Money {
            val incomeSum = incomes.fold(BigDecimal.ZERO) { total, income -> total.add(income.amount.amount) }
            val expenseSum = expenses.fold(BigDecimal.ZERO) { total, expense -> total.add(expense.amount.amount) }
            return Money.of(this.initialBalance.amount.add(incomeSum).subtract(expenseSum), this.currency)
        }

        override fun spendable(incomes: List<Income>, expenses: List<Expense>): Money =
            this.balance(incomes, expenses)
    }

    data class Credit(val creditLimit: Money, val initialDebt: Money) : AccountFunding {
        override val currency: Currency get() = this.creditLimit.currency

        override val overSpendMessage: String get() = "El monto supera el cupo disponible."

        override fun balance(incomes: List<Income>, expenses: List<Expense>): Money = this.currentDebt(expenses)

        override fun spendable(incomes: List<Income>, expenses: List<Expense>): Money =
            this.availableCredit(expenses)

        fun availableCredit(expenses: List<Expense>): Money =
            Money.of(this.creditLimit.amount.subtract(this.currentDebt(expenses).amount), this.currency)

        fun currentDebt(expenses: List<Expense>): Money {
            val expenseSum = expenses.fold(BigDecimal.ZERO) { total, expense -> total.add(expense.amount.amount) }
            return Money.of(this.initialDebt.amount.add(expenseSum), this.currency)
        }
    }
}
