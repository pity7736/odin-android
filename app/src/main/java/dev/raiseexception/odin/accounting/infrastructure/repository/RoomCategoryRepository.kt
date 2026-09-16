package dev.raiseexception.odin.accounting.infrastructure.repository

import android.database.sqlite.SQLiteException
import dev.raiseexception.odin.accounting.domain.CategoryLookupError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override suspend fun existsByNameAndType(name: String, type: CategoryType): Outcome<Boolean> =
        try {
            Outcome.Success(this.categoryDao.existsByNameAndType(name, type.name))
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to check category name"))
        }

    override suspend fun add(category: Category): Outcome<Unit> =
        try {
            this.categoryDao.insert(category.toEntity())
            Outcome.Success(Unit)
        } catch (e: SQLiteException) {
            Outcome.Failure(StorageError(e.message ?: "Failed to add category"))
        }

    override fun findById(id: String): Flow<Outcome<Category>> =
        this.categoryDao.findById(id).map<_, Outcome<Category>> { entity ->
            if (entity == null) {
                Outcome.Failure(
                    CategoryLookupError.NotFound(
                        internalMessage = "Category with id $id not found",
                        externalMessage = "Categoría no encontrada"
                    )
                )
            } else {
                Outcome.Success(entity.toDomain())
            }
        }.catch { e ->
            if (e is SQLiteException) {
                emit(Outcome.Failure(StorageError(e.message ?: "Failed to find category")))
            } else {
                throw e
            }
        }

    override fun getAll(): Flow<Outcome<List<Category>>> =
        this.categoryDao.getAll().map<_, Outcome<List<Category>>> { entities ->
            Outcome.Success(entities.map { it.toDomain() })
        }.catch { e ->
            if (e is SQLiteException) {
                emit(Outcome.Failure(StorageError(e.message ?: "Failed to list categories")))
            } else {
                throw e
            }
        }

    override fun findByType(type: CategoryType): Flow<Outcome<List<Category>>> =
        this.categoryDao.findByType(type.name).map<_, Outcome<List<Category>>> { entities ->
            Outcome.Success(entities.map { it.toDomain() })
        }.catch { e ->
            if (e is SQLiteException) {
                emit(Outcome.Failure(StorageError(e.message ?: "Failed to find categories by type")))
            } else {
                throw e
            }
        }
}
