package dev.raiseexception.odin.testutil

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.math.BigDecimal
import java.util.UUID

class AccountBuilder {
    private var id = UUID.randomUUID().toString()
    private var name = "Ahorros"
    private var initialBalance = Money.of(BigDecimal("100000.00"), Currency.COP)
    private var type = AccountType.SAVINGS
    private var description = ""
    private var createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private var incomes: List<Income> = emptyList()
    private var incomeCreationParams: List<IncomeCreationParams> = emptyList()

    fun id(id: String): AccountBuilder {
        this.id = id
        return this
    }

    fun name(name: String): AccountBuilder {
        this.name = name
        return this
    }

    fun initialBalance(initialBalance: Money): AccountBuilder {
        this.initialBalance = initialBalance
        return this
    }

    fun type(type: AccountType): AccountBuilder {
        this.type = type
        return this
    }

    fun description(description: String): AccountBuilder {
        this.description = description
        return this
    }

    fun createdAt(createdAt: Instant): AccountBuilder {
        this.createdAt = createdAt
        return this
    }

    fun incomes(incomes: List<Income>): AccountBuilder {
        this.incomes = incomes
        return this
    }

    fun withIncome(
        amount: String = "500.00",
        date: String = "2026-01-01",
        categoryId: String = "cat-1",
        description: String = "",
        clock: Clock = Clock.System
    ): AccountBuilder {
        this.incomeCreationParams += IncomeCreationParams(
            amount = amount,
            date = date,
            categoryId = categoryId,
            description = description,
            clock = clock
        )
        return this
    }

    fun build(): Account {
        val account = Account.restore(
            id = this.id,
            name = this.name,
            initialBalance = this.initialBalance,
            type = this.type,
            description = this.description,
            createdAt = this.createdAt
        )
        val createdIncomes = this.incomeCreationParams.map { params ->
            val outcome = account.createIncome(
                amount = params.amount,
                date = params.date,
                categoryId = params.categoryId,
                description = params.description,
                clock = params.clock
            )
            (outcome as Outcome.Success).value
        }
        return Account.restore(
            id = this.id,
            name = this.name,
            initialBalance = this.initialBalance,
            type = this.type,
            description = this.description,
            createdAt = this.createdAt,
            incomes = this.incomes + createdIncomes
        )
    }

    private data class IncomeCreationParams(
        val amount: String,
        val date: String,
        val categoryId: String,
        val description: String,
        val clock: Clock
    )
}
