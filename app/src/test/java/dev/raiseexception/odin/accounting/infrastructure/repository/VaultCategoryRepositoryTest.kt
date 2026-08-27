package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.infrastructure.serialization.CategoryRecord
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.InMemoryEncryptedRecordStore
import dev.raiseexception.odin.testutil.CategoryBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MASTER_KEY_SIZE = 32

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
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)
        val food = CategoryBuilder().build()

        repository.add(food)

        val plaintext = (store.readAll() as Outcome.Success).value.first().data.decodeToString()
        val record = json.decodeFromString(CategoryRecord.serializer(), plaintext)
        assertEquals(CategoryRecord.CATEGORY_RECORD_TYPE, record.recordType)
        assertEquals(food.id, record.id)
        assertEquals(food.name, record.name)
        assertEquals("EXPENSE", record.categoryType)
        assertEquals(food.description, record.description)
        assertEquals(food.color, record.color)
        assertEquals(food.createdAt.toString(), record.createdAt)
    }

    @Test
    fun `given a category saved, when checking same name and type case-insensitively, then returns true`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)
        repository.add(CategoryBuilder().build())

        val result = repository.existsByNameAndType("alimentación", CategoryType.EXPENSE)

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given a saved expense category, when checking the same name with income type, then returns false`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)
        repository.add(CategoryBuilder().name("Alquiler").build())

        val result = repository.existsByNameAndType("Alquiler", CategoryType.INCOME)

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given a category and an account saved, when reading, then only the category is returned`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val categoryRepository = VaultCategoryRepository(store, json)
        val accountRepository = VaultAccountRepository(store, json)
        categoryRepository.add(CategoryBuilder().build())
        accountRepository.add(account("Ahorros"))

        val result = categoryRepository.existsByNameAndType("Ahorros", CategoryType.EXPENSE)

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given a crypto failure on save, when adding, then returns CryptoFailure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))
        val repository = VaultCategoryRepository(store, json)

        val result = repository.add(CategoryBuilder().build())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryCreationError.CryptoFailure)
    }

    @Test
    fun `given a corrupt record exists, when reading all, then the corrupt record is skipped`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        store.save("corrupt", "not json at all".encodeToByteArray())
        val repository = VaultCategoryRepository(store, json)

        val result = repository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given no categories stored, when getAll, then emits empty list`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)

        val result = repository.getAll().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `given one income category stored, when getAll, then emits list with that category`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        repository.add(salary)

        val result = repository.getAll().first()

        assertEquals(1, result.size)
        assertEquals(salary.id, result.first().id)
        assertEquals("Salario", result.first().name)
        assertEquals(CategoryType.INCOME, result.first().type)
    }

    @Test
    fun `given income and expense categories stored, when getAll, then emits all categories`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultCategoryRepository(store, json)
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        repository.add(food)
        repository.add(salary)

        val result = repository.getAll().first()

        assertEquals(2, result.size)
    }
}
