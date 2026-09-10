package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.datetime.Instant
import java.math.BigDecimal

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val initialBalanceAmount: String,
    val currency: String,
    val type: String,
    val description: String,
    val createdAt: String
)

data class AccountWithTransactions(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId")
    val transactions: List<TransactionEntity>
)

internal fun AccountEntity.toDomain(incomes: List<Income>, expenses: List<Expense>): Account =
    Account.restore(
        id = id,
        name = name,
        initialBalance = Money.of(BigDecimal(initialBalanceAmount), Currency.valueOf(currency)),
        type = AccountType.valueOf(type),
        description = description,
        createdAt = Instant.parse(createdAt),
        incomes = incomes,
        expenses = expenses
    )

internal fun Account.toEntity(): AccountEntity =
    AccountEntity(
        id = id,
        name = name,
        initialBalanceAmount = initialBalance.amount.toPlainString(),
        currency = currency.name,
        type = type.name,
        description = description,
        createdAt = createdAt.toString()
    )
