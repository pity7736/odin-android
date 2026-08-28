package dev.raiseexception.odin.testutil

import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Money
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

    fun build(): Account = Account.restore(
        id = id,
        name = name,
        initialBalance = initialBalance,
        type = type,
        description = description,
        createdAt = createdAt,
    )
}
