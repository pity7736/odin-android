package dev.raiseexception.odin.testutil

import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.util.UUID

class ExpenseBuilder {
    private var id: String? = null
    private var accountId = "acc-1"
    private var amount = Money.of(BigDecimal("500.00"), Currency.COP)
    private var date = LocalDate.parse("2026-10-01")
    private var categoryId = "cat-1"
    private var description = ""
    private var createdAt = Instant.parse("2026-10-01T10:00:00Z")
    private var tagIds: List<String> = emptyList()

    fun id(id: String): ExpenseBuilder {
        this.id = id
        return this
    }

    fun accountId(accountId: String): ExpenseBuilder {
        this.accountId = accountId
        return this
    }

    fun amount(amount: String, currency: Currency = Currency.COP): ExpenseBuilder {
        this.amount = Money.of(BigDecimal(amount), currency)
        return this
    }

    fun date(date: String): ExpenseBuilder {
        this.date = LocalDate.parse(date)
        return this
    }

    fun categoryId(categoryId: String): ExpenseBuilder {
        this.categoryId = categoryId
        return this
    }

    fun description(description: String): ExpenseBuilder {
        this.description = description
        return this
    }

    fun createdAt(createdAt: Instant): ExpenseBuilder {
        this.createdAt = createdAt
        return this
    }

    fun tagIds(tagIds: List<String>): ExpenseBuilder {
        this.tagIds = tagIds
        return this
    }

    fun build(): Expense = Expense.restore(
        id = this.id ?: UUID.randomUUID().toString(),
        accountId = this.accountId,
        amount = this.amount,
        date = this.date,
        categoryId = this.categoryId,
        description = this.description,
        createdAt = this.createdAt,
        tagIds = this.tagIds,
    )
}
