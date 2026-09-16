package dev.raiseexception.odin.di

import com.github.f4b6a3.uuid.UuidCreator
import dev.raiseexception.odin.accounting.application.usecase.AccountLister
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.application.usecase.IncomeCreator
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryInput
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountRepository
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import java.math.BigDecimal

class DevDataSeeder(
    private val accountRepository: AccountRepository,
    private val categoryCreator: CategoryCreator,
    private val categoryRepository: CategoryRepository,
    private val incomeCreator: IncomeCreator,
    private val accountLister: AccountLister,
) {

    @Suppress("LongMethod")
    suspend fun seed() {
        val existingAccounts = this.accountLister.list().first()
        if (existingAccounts is Outcome.Success && existingAccounts.value.isNotEmpty()) return

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val yesterday = today.minus(DAYS_AGO_YESTERDAY, DateTimeUnit.DAY)
        val lastWeek = today.minus(DAYS_AGO_LAST_WEEK, DateTimeUnit.DAY)
        val twoWeeksAgo = Clock.System.now().minus(
            DAYS_AGO_TWO_WEEKS,
            DateTimeUnit.DAY,
            TimeZone.currentSystemDefault()
        )

        val savingsAccount = Account.restore(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            name = "Ahorros",
            initialBalance = Money.of(BigDecimal("1000000"), Currency.COP),
            type = AccountType.SAVINGS,
            description = "",
            createdAt = twoWeeksAgo
        )
        this.accountRepository.add(savingsAccount)
        val cashAccount = Account.restore(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            name = "Efectivo",
            initialBalance = Money.of(BigDecimal("50000"), Currency.COP),
            type = AccountType.CASH,
            description = "",
            createdAt = twoWeeksAgo
        )
        this.accountRepository.add(cashAccount)

        this.categoryCreator.create("Alimentación", CategoryType.EXPENSE, "", null)
        this.categoryCreator.create("Transporte", CategoryType.EXPENSE, "", null)
        this.categoryCreator.create("Entretenimiento", CategoryType.EXPENSE, "", null)

        val transferCategory = Category.restore(
            id = UuidCreator.getTimeOrderedEpoch().toString(),
            name = "Transferencia",
            type = CategoryType.TRANSFER,
            description = "",
            color = "#607D8B",
            createdAt = Clock.System.now(),
            isSystem = true
        )
        this.categoryRepository.add(transferCategory)

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
        private const val DAYS_AGO_TWO_WEEKS = 14
    }
}
