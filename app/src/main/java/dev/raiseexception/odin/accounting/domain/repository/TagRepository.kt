package dev.raiseexception.odin.accounting.domain.repository

import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun getAll(): Flow<Outcome<List<Tag>>>
    suspend fun findById(id: String): Outcome<Tag?>
    suspend fun findByNormalizedName(normalizedName: String): Outcome<Tag?>
    suspend fun add(tag: Tag): Outcome<Unit>
    suspend fun deleteUnused(): Outcome<Unit>
}
