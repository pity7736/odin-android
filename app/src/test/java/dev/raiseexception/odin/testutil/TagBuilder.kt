package dev.raiseexception.odin.testutil

import dev.raiseexception.odin.accounting.domain.model.Tag
import kotlinx.datetime.Instant
import java.util.UUID

class TagBuilder {
    private var id: String? = null
    private var name = "Nala"
    private var createdAt = Instant.parse("2026-01-01T00:00:00Z")

    fun id(id: String): TagBuilder {
        this.id = id
        return this
    }

    fun name(name: String): TagBuilder {
        this.name = name
        return this
    }

    fun createdAt(createdAt: Instant): TagBuilder {
        this.createdAt = createdAt
        return this
    }

    fun build(): Tag = Tag.restore(
        this.id ?: UUID.randomUUID().toString(),
        this.name,
        Tag.normalize(this.name),
        this.createdAt,
    )
}
