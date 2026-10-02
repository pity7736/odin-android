package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"]),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"])
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("type")]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val accountId: String,
    val amount: String,
    val currency: String,
    val date: String,
    val categoryId: String,
    val description: String,
    val createdAt: String
)

data class TransactionWithTagIds(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "expenseId",
        entity = ExpenseTagEntity::class,
        projection = ["tagId"]
    )
    val tagIds: List<String>
)

internal fun TransactionEntity.toIncome(): Income =
    Income.restore(
        id = id,
        accountId = accountId,
        amount = Money.of(BigDecimal(amount), Currency.valueOf(currency)),
        date = LocalDate.parse(date),
        categoryId = categoryId,
        description = description,
        createdAt = Instant.parse(createdAt)
    )

internal fun TransactionEntity.toExpense(tagIds: List<String>): Expense =
    Expense.restore(
        id = id,
        accountId = accountId,
        amount = Money.of(BigDecimal(amount), Currency.valueOf(currency)),
        date = LocalDate.parse(date),
        categoryId = categoryId,
        description = description,
        createdAt = Instant.parse(createdAt),
        tagIds = tagIds
    )

internal fun Income.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        type = "INCOME",
        accountId = accountId,
        amount = amount.amount.toPlainString(),
        currency = amount.currency.name,
        date = date.toString(),
        categoryId = categoryId,
        description = description,
        createdAt = createdAt.toString()
    )

internal fun Expense.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        type = "EXPENSE",
        accountId = accountId,
        amount = amount.amount.toPlainString(),
        currency = amount.currency.name,
        date = date.toString(),
        categoryId = categoryId,
        description = description,
        createdAt = createdAt.toString()
    )
