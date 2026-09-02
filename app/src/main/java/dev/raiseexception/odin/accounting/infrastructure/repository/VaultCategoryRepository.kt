package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.infrastructure.serialization.CategoryRecord
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class VaultCategoryRepository(
    private val encryptedRecordStore: EncryptedRecordStore,
    private val json: Json = Json
) : CategoryRepository {

    override suspend fun existsByNameAndType(name: String, type: CategoryType): Outcome<Boolean> {
        val categoryRecords = when (val decryptOutcome = this.decryptedCategoryRecords()) {
            is Outcome.Success -> decryptOutcome.value
            is Outcome.Failure -> return decryptOutcome
        }
        return Outcome.Success(
            categoryRecords.any { it.name.equals(name, ignoreCase = true) && it.categoryType == type.name }
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

    override suspend fun add(category: Category): Outcome<Unit> {
        val plaintext = this.json.encodeToString(CategoryRecord.serializer(), this.toRecord(category))
            .encodeToByteArray()
        return when (val saveOutcome = this.encryptedRecordStore.save(category.id, plaintext)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> this.cryptoFailure(saveOutcome.error.internalMessage)
        }
    }

    private fun toCategory(record: CategoryRecord): Category = Category.restore(
        id = record.id,
        name = record.name,
        type = CategoryType.valueOf(record.categoryType),
        description = record.description,
        color = record.color,
        createdAt = Instant.parse(record.createdAt)
    )

    private suspend fun decryptedCategoryRecords(): Outcome<List<CategoryRecord>> {
        val records = when (val readOutcome = this.encryptedRecordStore.readAll()) {
            is Outcome.Success -> readOutcome.value
            is Outcome.Failure -> return this.cryptoFailure(readOutcome.error.internalMessage)
        }
        val categoryRecords = records
            .mapNotNull { record ->
                try {
                    this.json.decodeFromString(CategoryRecord.serializer(), record.data.decodeToString())
                } catch (@Suppress("SwallowedException") exception: SerializationException) {
                    null
                }
            }
            .filter { it.recordType == CategoryRecord.CATEGORY_RECORD_TYPE }
        return Outcome.Success(categoryRecords)
    }

    private fun toRecord(category: Category) = CategoryRecord(
        recordType = CategoryRecord.CATEGORY_RECORD_TYPE,
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
