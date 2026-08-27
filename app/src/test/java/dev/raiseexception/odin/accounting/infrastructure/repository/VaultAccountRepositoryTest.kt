package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.AccountType
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.infrastructure.serialization.AccountRecord
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

class VaultAccountRepositoryTest {

    private val vaultCrypto = BouncyCastleVaultCrypto()
    private val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
    private val json = Json

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
            description = "Fondo de emergencia"
        ) as Outcome.Success
        ).value

    @Test
    fun `given an added account, when inspecting the stored blob, then it is encrypted`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val savings = account("Ahorros")

        repository.add(savings)

        val storedBlob = store.entries.getValue(savings.id)
        assertFalse(storedBlob.decodeToString().contains("Ahorros"))
    }

    @Test
    fun `given an added account, when reading the stored record, then all fields are intact`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val savings = account("Ahorros")

        repository.add(savings)

        val plaintext = (store.readAll() as Outcome.Success).value.first().data.decodeToString()
        val record = json.decodeFromString(AccountRecord.serializer(), plaintext)
        assertEquals(AccountRecord.ACCOUNT_RECORD_TYPE, record.recordType)
        assertEquals(savings.id, record.id)
        assertEquals("Ahorros", record.name)
        assertEquals("1500.00", record.amount)
        assertEquals("COP", record.currency)
        assertEquals("SAVINGS", record.accountType)
        assertEquals("Fondo de emergencia", record.description)
        assertEquals(savings.createdAt.toString(), record.createdAt)
    }

    @Test
    fun `given an existing account, when checking the same name, then returns true`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        repository.add(account("Ahorros"))

        val result = repository.existsByName("Ahorros")

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given an existing account, when checking a different case of the name, then returns true`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        repository.add(account("Ahorros"))

        val result = repository.existsByName("ahorros")

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value)
    }

    @Test
    fun `given an existing account, when checking a different name, then returns false`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        repository.add(account("Ahorros"))

        val result = repository.existsByName("Gastos")

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given a store crypto failure, when adding, then returns crypto failure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))
        val repository = VaultAccountRepository(store, json)

        val result = repository.add(account("Ahorros"))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.CryptoFailure)
    }

    @Test
    fun `given a store crypto failure, when checking a name, then returns crypto failure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))
        val repository = VaultAccountRepository(store, json)

        val result = repository.existsByName("Ahorros")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountCreationError.CryptoFailure)
    }

    @Test
    fun `given a corrupt stored record, when checking a name, then skips it and returns false`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        store.save("corrupt", "not json at all".encodeToByteArray())
        val repository = VaultAccountRepository(store, json)

        val result = repository.existsByName("Ahorros")

        assertTrue(result is Outcome.Success)
        assertFalse((result as Outcome.Success).value)
    }

    @Test
    fun `given no accounts, when getting all, then emits empty list`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)

        val result = mutableListOf<List<Account>>()
        repository.getAll().collect { result.add(it) }

        assertEquals(1, result.size)
        assertTrue(result.first().isEmpty())
    }

    @Test
    fun `given one account added, when getting all, then emits a list with that account`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val savings = account("Ahorros")
        repository.add(savings)

        val result = mutableListOf<List<Account>>()
        repository.getAll().collect { result.add(it) }

        assertEquals(1, result.size)
        assertEquals(1, result.first().size)
        assertEquals(savings.id, result.first().first().id)
    }

    @Test
    fun `given multiple accounts added, when getting all, then emits all accounts ordered by id ascending`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val checking = account("Corriente")
        val savings = account("Ahorros")
        repository.add(checking)
        repository.add(savings)

        val result = mutableListOf<List<Account>>()
        repository.getAll().collect { result.add(it) }

        assertEquals(1, result.size)
        val accounts = result.first()
        assertEquals(2, accounts.size)
        assertTrue(accounts[0].id <= accounts[1].id)
    }

    @Test
    fun `given one account added, when getting all, then all fields round-trip correctly`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val savings = account("Ahorros")
        repository.add(savings)

        val result = mutableListOf<List<Account>>()
        repository.getAll().collect { result.add(it) }

        val restored = result.first().first()
        assertEquals(savings.id, restored.id)
        assertEquals(savings.name, restored.name)
        assertEquals(savings.initialBalance, restored.initialBalance)
        assertEquals(savings.type, restored.type)
        assertEquals(savings.description, restored.description)
        assertEquals(savings.createdAt, restored.createdAt)
    }
}
