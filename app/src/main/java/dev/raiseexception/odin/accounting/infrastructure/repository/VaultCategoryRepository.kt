package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.repository.CategoryRepository
import dev.raiseexception.odin.accounting.infrastructure.serialization.CategoryRecord
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.EncryptedRecordStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class VaultCategoryRepository(
    private val encryptedRecordStore: EncryptedRecordStore,
    private val json: Json = Json
) : CategoryRepository {

    override suspend fun existsByName(name: String): Outcome<Boolean> {
        val categoryRecords = when (val decryptOutcome = this.decryptedCategoryRecords()) {
            is Outcome.Success -> decryptOutcome.value
            is Outcome.Failure -> return decryptOutcome
        }
        return Outcome.Success(categoryRecords.any { it.name.equals(name, ignoreCase = true) })
    }

    override suspend fun add(category: Category): Outcome<Unit> {
        val plaintext = this.json.encodeToString(CategoryRecord.serializer(), this.toRecord(category))
            .encodeToByteArray()
        return when (val saveOutcome = this.encryptedRecordStore.save(category.id, plaintext)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> this.cryptoFailure(saveOutcome.error.internalMessage)
        }
    }

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
