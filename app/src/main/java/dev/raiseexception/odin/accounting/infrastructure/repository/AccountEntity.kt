package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
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
    val initialBalanceAmount: String?,
    val currency: String,
    val type: String,
    val description: String,
    val createdAt: String,
    val creditLimitAmount: String? = null,
    val debtAmount: String? = null
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
        funding = toFunding(),
        type = AccountType.valueOf(type),
        description = description,
        createdAt = Instant.parse(createdAt),
        incomes = incomes,
        expenses = expenses
    )

private fun AccountEntity.toFunding(): AccountFunding = when (AccountType.valueOf(type)) {
    AccountType.CREDIT_CARD -> AccountFunding.Credit(
        creditLimit = Money.of(BigDecimal(creditLimitAmount!!), Currency.valueOf(currency)),
        debt = Money.of(BigDecimal(debtAmount!!), Currency.valueOf(currency))
    )
    AccountType.SAVINGS, AccountType.CASH ->
        AccountFunding.Funds(Money.of(BigDecimal(initialBalanceAmount!!), Currency.valueOf(currency)))
}

internal fun Account.toEntity(): AccountEntity = when (val accountFunding = funding) {
    is AccountFunding.Funds -> AccountEntity(
        id = id,
        name = name,
        initialBalanceAmount = accountFunding.initialBalance.amount.toPlainString(),
        currency = currency.name,
        type = type.name,
        description = description,
        createdAt = createdAt.toString(),
        creditLimitAmount = null,
        debtAmount = null
    )
    is AccountFunding.Credit -> AccountEntity(
        id = id,
        name = name,
        initialBalanceAmount = null,
        currency = currency.name,
        type = type.name,
        description = description,
        createdAt = createdAt.toString(),
        creditLimitAmount = accountFunding.creditLimit.amount.toPlainString(),
        debtAmount = accountFunding.debt.amount.toPlainString()
    )
}
