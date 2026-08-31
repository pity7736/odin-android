package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.CategoryCreationError
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.persistence.CategoryEntity
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.testutil.CategoryBuilder
import dev.raiseexception.odin.testutil.FakeCategoryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MASTER_KEY_SIZE = 32

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VaultCategoryRepositoryTest {

    private val vaultCrypto = BouncyCastleVaultCrypto()
    private val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
    private val json = Json

    private fun repositoryWith(masterKeyRepository: FakeMasterKeyRepository) = VaultCategoryRepository(
        categoryDao = FakeCategoryDao(),
        vaultCrypto = vaultCrypto,
        masterKeyRepository = masterKeyRepository,
        ioDispatcher = UnconfinedTestDispatcher(),
        json = json
    )

    @Test
    fun `given a saved category, when reading all, then all fields are intact`() = runTest {
        val masterKeyRepository = FakeMasterKeyRepository(masterKey)
        val repository = repositoryWith(masterKeyRepository)
        val food = CategoryBuilder().build()

        repository.add(food)

        val result = repository.getAll().first()
        assertTrue(result is Outcome.Success)
        val categories = (result as Outcome.Success).value
        assertEquals(1, categories.size)
        val saved = categories.first()
        assertEquals(food.id, saved.id)
        assertEquals(food.name, saved.name)
        assertEquals(food.type, saved.type)
        assertEquals(food.description, saved.description)
        assertEquals(food.color, saved.color)
        assertEquals(food.createdAt, saved.createdAt)
    }

    @Test
    fun `given a category saved, when checking same name and type case-insensitively, then returns true`() = runTest {
        val repository = repositoryWith(FakeMasterKeyRepository(masterKey))
        repository.add(CategoryBuilder().build())

        val result = repository.existsByNameAndType("alimentación", CategoryType.EXPENSE)

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given a saved expense category, when checking the same name with income type, then returns false`() = runTest {
        val repository = repositoryWith(FakeMasterKeyRepository(masterKey))
        repository.add(CategoryBuilder().name("Alquiler").build())

        val result = repository.existsByNameAndType("Alquiler", CategoryType.INCOME)

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given no categories stored, when getAll, then emits success with empty list`() = runTest {
        val repository = repositoryWith(FakeMasterKeyRepository(masterKey))

        val result = repository.getAll().first()

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `given one income category stored, when getAll, then emits list with that category`() = runTest {
        val repository = repositoryWith(FakeMasterKeyRepository(masterKey))
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        repository.add(salary)

        val result = repository.getAll().first()

        assertTrue(result is Outcome.Success)
        val categories = (result as Outcome.Success).value
        assertEquals(1, categories.size)
        assertEquals(salary.id, categories.first().id)
        assertEquals("Salario", categories.first().name)
        assertEquals(CategoryType.INCOME, categories.first().type)
    }

    @Test
    fun `given income and expense categories stored, when getAll, then emits all categories`() = runTest {
        val repository = repositoryWith(FakeMasterKeyRepository(masterKey))
        val food = CategoryBuilder().build()
        val salary = CategoryBuilder().name("Salario").type(CategoryType.INCOME).build()
        repository.add(food)
        repository.add(salary)

        val result = repository.getAll().first()

        assertTrue(result is Outcome.Success)
        assertEquals(2, (result as Outcome.Success).value.size)
    }

    @Test
    fun `given a corrupt record exists, when reading all, then the corrupt record is skipped`() = runTest {
        val masterKeyRepository = FakeMasterKeyRepository(masterKey)
        val categoryDao = FakeCategoryDao()
        val repository = VaultCategoryRepository(
            categoryDao = categoryDao,
            vaultCrypto = vaultCrypto,
            masterKeyRepository = masterKeyRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
            json = json
        )
        val encryptResult = vaultCrypto.encrypt("not json at all".encodeToByteArray(), masterKey)
        val corruptCiphertext = (encryptResult as Outcome.Success).value
        categoryDao.insert(CategoryEntity("corrupt", corruptCiphertext))

        val result = repository.existsByNameAndType("Alimentación", CategoryType.EXPENSE)

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given a crypto failure on save, when adding, then returns CryptoFailure`() = runTest {
        val repository = repositoryWith(FakeMasterKeyRepository(null))

        val result = repository.add(CategoryBuilder().build())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CategoryCreationError.CryptoFailure)
    }

    @Test
    fun `given a store crypto failure, when getting all, then emits failure`() = runTest {
        val repository = repositoryWith(FakeMasterKeyRepository(null))

        val result = repository.getAll().first()

        assertTrue(result is Outcome.Failure)
    }
}
