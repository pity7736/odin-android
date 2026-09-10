package dev.raiseexception.odin.accounting.infrastructure.repository

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import kotlinx.datetime.Instant

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val description: String,
    val color: String,
    val createdAt: String
)

internal fun CategoryEntity.toDomain(): Category =
    Category.restore(
        id = id,
        name = name,
        type = CategoryType.valueOf(type),
        description = description,
        color = color,
        createdAt = Instant.parse(createdAt)
    )

internal fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        type = type.name,
        description = description,
        color = color,
        createdAt = createdAt.toString()
    )
