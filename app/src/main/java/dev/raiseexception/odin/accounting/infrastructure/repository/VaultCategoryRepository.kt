package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.infrastructure.serialization.CategoryRecord
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.persistence.CategoryDao
import dev.raiseexception.odin.persistence.CategoryEntity
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class VaultCategoryRepository(
    private val categoryDao: CategoryDao,
    private val vaultCrypto: VaultCrypto,
    private val masterKeyRepository: MasterKeyRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json
) : CategoryRepository {

    override suspend fun existsByNameAndType(name: String, type: CategoryType): Outcome<Boolean> {
        val records = when (val outcome = this.decryptedCategoryRecords()) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return outcome
        }
        return Outcome.Success(
            records.any { it.name.equals(name, ignoreCase = true) && it.categoryType == type.name }
        )
    }

    override fun getAll(): Flow<Outcome<List<Category>>> = flow {
        when (val outcome = this@VaultCategoryRepository.decryptedCategoryRecords()) {
            is Outcome.Failure -> emit(outcome)
            is Outcome.Success -> emit(
                Outcome.Success(
                    outcome.value.sortedBy { it.id }.map { this@VaultCategoryRepository.toCategory(it) }
                )
            )
        }
    }

    override suspend fun add(category: Category): Outcome<Unit> = withContext(this.ioDispatcher) {
        val masterKey = when (val outcome = masterKeyRepository.get()) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return@withContext cryptoFailure(outcome.error.internalMessage)
        }
        val plaintext = json.encodeToString(CategoryRecord.serializer(), toRecord(category)).encodeToByteArray()
        val ciphertext = when (val outcome = vaultCrypto.encrypt(plaintext, masterKey)) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> return@withContext cryptoFailure(outcome.error.internalMessage)
        }
        try {
            categoryDao.insert(CategoryEntity(category.id, ciphertext))
            Outcome.Success(Unit)
        } catch (exception: android.database.SQLException) {
            Outcome.Failure(
                CategoryCreationError.StorageFailure(
                    internalMessage = exception.message ?: "Storage failure",
                    externalMessage = "Algo salió mal. Intente de nuevo más tarde"
                )
            )
        }
    }

    private suspend fun decryptedCategoryRecords(): Outcome<List<CategoryRecord>> =
        withContext(this.ioDispatcher) {
            val masterKey = when (val outcome = masterKeyRepository.get()) {
                is Outcome.Success -> outcome.value
                is Outcome.Failure -> return@withContext cryptoFailure(outcome.error.internalMessage)
            }
            val entities = categoryDao.getAll()
            val records = entities.mapNotNull { entity ->
                when (val outcome = vaultCrypto.decrypt(entity.data, masterKey)) {
                    is Outcome.Failure -> return@withContext cryptoFailure(outcome.error.internalMessage)
                    is Outcome.Success -> try {
                        json.decodeFromString(CategoryRecord.serializer(), outcome.value.decodeToString())
                    } catch (@Suppress("SwallowedException") exception: SerializationException) {
                        null
                    }
                }
            }
            Outcome.Success(records)
        }

    private fun toCategory(record: CategoryRecord): Category = Category.restore(
        id = record.id,
        name = record.name,
        type = CategoryType.valueOf(record.categoryType),
        description = record.description,
        color = record.color,
        createdAt = Instant.parse(record.createdAt)
    )

    private fun toRecord(category: Category) = CategoryRecord(
        id = category.id,
        name = category.name,
        categoryType = category.type.name,
        description = category.description,
        color = category.color,
        createdAt = category.createdAt.toString()
    )

    private fun cryptoFailure(internalMessage: String) = Outcome.Failure(
        CategoryCreationError.CryptoFailure(
            internalMessage = internalMessage,
            externalMessage = "Algo salió mal. Intente de nuevo más tarde"
        )
    )
}
