package dev.raiseexception.odin.testutil

import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import kotlinx.datetime.Instant
import java.util.UUID

class CategoryBuilder {
    private var id: String? = null
    private var name = "Alimentación"
    private var type = CategoryType.EXPENSE
    private var description = ""
    private var color = "#E57373"
    private var createdAt = Instant.parse("2026-01-01T00:00:00Z")

    fun id(id: String): CategoryBuilder {
        this.id = id
        return this
    }

    fun name(name: String): CategoryBuilder {
        this.name = name
        return this
    }

    fun type(type: CategoryType): CategoryBuilder {
        this.type = type
        return this
    }

    fun description(description: String): CategoryBuilder {
        this.description = description
        return this
    }

    fun color(color: String): CategoryBuilder {
        this.color = color
        return this
    }

    fun createdAt(createdAt: Instant): CategoryBuilder {
        this.createdAt = createdAt
        return this
    }

    fun build(): Category = Category.restore(
        this.id ?: UUID.randomUUID().toString(),
        this.name,
        this.type,
        this.description,
        this.color,
        this.createdAt,
    )
}
