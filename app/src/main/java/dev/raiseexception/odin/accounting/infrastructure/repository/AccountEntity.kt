package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountFunding
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.GmfTreatment
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.model.MoneyAccountKind
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal

private val GMF_TAG_NAMES_SERIALIZER = ListSerializer(String.serializer())

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["gmfCategoryId"])
    ],
    indices = [Index("gmfCategoryId")]
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val initialBalanceAmount: String?,
    val currency: String,
    val type: String,
    val description: String,
    val createdAt: String,
    val creditLimitAmount: String? = null,
    val debtAmount: String? = null,
    val gmfCategoryId: String? = null,
    val gmfTagNames: String? = null
)

data class AccountWithTransactions(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId", entity = TransactionEntity::class)
    val transactions: List<TransactionWithTagIds>
)

internal fun AccountEntity.toDomain(incomes: List<Income>, expenses: List<Expense>): Account =
    Account.restore(
        id = id,
        name = name,
        funding = toFunding(),
        description = description,
        createdAt = Instant.parse(createdAt),
        incomes = incomes,
        expenses = expenses
    )

private fun AccountEntity.toFunding(): AccountFunding = when (AccountType.valueOf(type)) {
    AccountType.CREDIT_CARD -> AccountFunding.Credit(
        creditLimit = Money.of(BigDecimal(creditLimitAmount!!), Currency.valueOf(currency)),
        initialDebt = Money.of(BigDecimal(debtAmount!!), Currency.valueOf(currency))
    )
    AccountType.SAVINGS -> toFunds(MoneyAccountKind.SAVINGS)
    AccountType.CASH -> toFunds(MoneyAccountKind.CASH)
}

private fun AccountEntity.toFunds(kind: MoneyAccountKind): AccountFunding =
    AccountFunding.Funds(
        Money.of(BigDecimal(initialBalanceAmount!!), Currency.valueOf(currency)),
        kind,
        toGmfTreatment()
    )

private fun AccountEntity.toGmfTreatment(): GmfTreatment {
    val categoryId = gmfCategoryId ?: return GmfTreatment.Exempt
    val tagNames = gmfTagNames?.let { Json.decodeFromString(GMF_TAG_NAMES_SERIALIZER, it) } ?: emptyList()
    return GmfTreatment.Taxed(categoryId, tagNames)
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
        debtAmount = null,
        gmfCategoryId = (accountFunding.gmf as? GmfTreatment.Taxed)?.categoryId,
        gmfTagNames = (accountFunding.gmf as? GmfTreatment.Taxed)?.let {
            Json.encodeToString(GMF_TAG_NAMES_SERIALIZER, it.tagNames)
        }
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
        debtAmount = accountFunding.initialDebt.amount.toPlainString()
    )
}
