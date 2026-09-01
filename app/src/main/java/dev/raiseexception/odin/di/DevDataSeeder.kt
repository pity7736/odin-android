package dev.raiseexception.odin.di

import dev.raiseexception.odin.accounting.application.usecase.AccountCreator
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class DevDataSeeder(
    private val accountCreator: AccountCreator,
    private val categoryCreator: CategoryCreator,
    private val incomeCreator: IncomeCreator,
) {

    suspend fun seed() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val yesterday = today.minus(DAYS_AGO_YESTERDAY, DateTimeUnit.DAY)
        val lastWeek = today.minus(DAYS_AGO_LAST_WEEK, DateTimeUnit.DAY)

        val savingsAccount = when (
            val result = this.accountCreator.create("Ahorros", "1000000", Currency.COP, AccountType.SAVINGS, "")
        ) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return
        }
        this.accountCreator.create("Efectivo", "50000", Currency.COP, AccountType.CASH, "")

        this.categoryCreator.create("Alimentación", CategoryType.EXPENSE, "", null)
        this.categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null)
        this.categoryCreator.create("Entretenimiento", CategoryType.EXPENSE, "", null)

        this.incomeCreator.create(
            accountId = savingsAccount.id,
            amount = "2000000",
            date = yesterday.toString(),
            categoryInput = CategoryInput.New("Salario"),
            description = "Pago mensual"
        )
        this.incomeCreator.create(
            accountId = savingsAccount.id,
            amount = "500000",
            date = lastWeek.toString(),
            categoryInput = CategoryInput.New("Freelance"),
            description = "Proyecto web"
        )
    }

    companion object {
        private const val DAYS_AGO_YESTERDAY = 1
        private const val DAYS_AGO_LAST_WEEK = 7
    }
}
