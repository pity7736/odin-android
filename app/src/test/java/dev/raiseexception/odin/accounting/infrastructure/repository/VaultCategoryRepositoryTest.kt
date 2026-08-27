package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Category
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.infrastructure.serialization.CategoryRecord
import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.InMemoryEncryptedRecordStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MASTER_KEY_SIZE = 32

private class CategoryFakeMasterKeyRepository(private var masterKey: ByteArray?) : MasterKeyRepository {
    override fun store(masterKey: ByteArray) {
        this.masterKey = masterKey
    }

    override fun get(): Outcome<ByteArray> {
        val key = this.masterKey ?: return Outcome.Failure(CryptoError.MasterKeyNotFound())
        return Outcome.Success(key)
    }

    override fun clear() {
        this.masterKey = null
    }
}

class VaultCategoryRepositoryTest {

    private val vaultCrypto = BouncyCastleVaultCrypto()
    private val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
    private val json = Json

    @Suppress("ExperimentalCoroutinesApi")
    private fun storeWith(masterKeyRepository: MasterKeyRepository) = InMemoryEncryptedRecordStore(
        vaultCrypto = vaultCrypto,
        masterKeyRepository = masterKeyRepository,
        cpuDispatcher = UnconfinedTestDispatcher()
    )

    private fun category(name: String) = (
        Category.create(
            name = name,
            type = CategoryType.EXPENSE,
            description = "Categoría de prueba",
            color = "#E57373"
        ) as Outcome.Success
        ).value

    private fun account(name: String) = (
        Account.create(
            name = name,
            initialBalance = "1500.00",
            currency = Currency.COP,
            type = AccountType.SAVINGS,
            description = "Cuenta de prueba"
        ) as Outcome.Success
        ).value

    @Test
    fun `given a saved category, when reading all, then all fields are intact`() = runTest {
        val store = storeWith(CategoryFakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)
        val food = category("Alimentación")

        repository.add(food)

        val plaintext = (store.readAll() as Outcome.Success).value.first().data.decodeToString()
        val record = json.decodeFromString(CategoryRecord.serializer(), plaintext)
        assertEquals(CategoryRecord.CATEGORY_RECORD_TYPE, record.recordType)
        assertEquals(food.id, record.id)
        assertEquals("Alimentación", record.name)
        assertEquals("EXPENSE", record.categoryType)
        assertEquals("Categoría de prueba", record.description)
        assertEquals("#E57373", record.color)
        assertEquals(food.createdAt.toString(), record.createdAt)
    }

    @Test
    fun `given a saved category, when checking the same name case-insensitively, then returns true`() = runTest {
        val store = storeWith(CategoryFakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)
        repository.add(category("Alimentación"))

        val result = repository.existsByName("alimentación")

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given a category and an account saved, when reading, then only the category is returned`() = runTest {
        val store = storeWith(CategoryFakeMasterKeyRepository(masterKey))
        val categoryRepository = VaultCategoryRepository(store, json)
        val accountRepository = VaultAccountRepository(store, json)
        categoryRepository.add(category("Alimentación"))
        accountRepository.add(account("Ahorros"))

        val result = categoryRepository.existsByName("Ahorros")

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given a crypto failure on save, when adding, then returns CryptoFailure`() = runTest {
        val store = storeWith(CategoryFakeMasterKeyRepository(null))
        val repository = VaultCategoryRepository(store, json)

        val result = repository.add(category("Alimentación"))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryCreationError.CryptoFailure)
    }

    @Test
    fun `given a corrupt record exists, when reading all, then the corrupt record is skipped`() = runTest {
        val store = storeWith(CategoryFakeMasterKeyRepository(masterKey))
        store.save("corrupt", "not json at all".encodeToByteArray())
        val repository = VaultCategoryRepository(store, json)

        val result = repository.existsByName("Alimentación")

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }
}
