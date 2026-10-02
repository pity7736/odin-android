package dev.raiseexception.odin.accounting.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounting.domain.model.Tag
import dev.raiseexception.odin.accounting.domain.repository.TagRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomTagRepository(
    private val tagDao: TagDao
) : TagRepository {

    override fun getAll(): Flow<Outcome<List<Tag>>> =
        this.tagDao.getAll().map<_, Outcome<List<Tag>>> { entities ->
            Outcome.Success(entities.map { it.toDomain() }.sortedWith(Tag.ALPHABETICAL_ORDER))
        }.catch { e ->
            if (e is SQLiteException) {
                emit(Outcome.Failure(StorageError(e.message ?: "Failed to list tags")))
            } else {
                throw e
            }
        }

    override suspend fun findById(id: String): Outcome<Tag?> =
        try {
            Outcome.Success(this.tagDao.findById(id)?.toDomain())
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to find tag"))
        }

    override suspend fun findByNormalizedName(normalizedName: String): Outcome<Tag?> =
        try {
            Outcome.Success(this.tagDao.findByNormalizedName(normalizedName)?.toDomain())
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to find tag by name"))
        }

    override suspend fun add(tag: Tag): Outcome<Unit> =
        try {
            this.tagDao.insert(tag.toEntity())
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to add tag"))
        }

    override suspend fun deleteUnused(): Outcome<Unit> =
        try {
            this.tagDao.deleteUnused()
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to delete unused tags"))
        }
}
