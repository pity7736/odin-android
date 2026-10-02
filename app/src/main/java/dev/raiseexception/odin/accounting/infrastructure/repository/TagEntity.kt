package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.raiseexception.odin.accounting.domain.model.Tag
import kotlinx.datetime.Instant

@Entity(
    tableName = "tags",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val createdAt: String
)

internal fun TagEntity.toDomain(): Tag =
    Tag.restore(
        id = id,
        name = name,
        normalizedName = normalizedName,
        createdAt = Instant.parse(createdAt)
    )

internal fun Tag.toEntity(): TagEntity =
    TagEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        createdAt = createdAt.toString()
    )
