package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.AccountCreationError
import dev.raiseexception.odin.accounting.domain.AccountLookupError
import dev.raiseexception.odin.accounting.domain.model.Account
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.accounting.domain.repository.AccountCriteria
import dev.raiseexception.odin.accounting.infrastructure.serialization.AccountRecord
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.InMemoryEncryptedRecordStore
import dev.raiseexception.odin.testutil.AccountBuilder
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

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

    private fun account(name: String) = AccountBuilder()
        .name(name)
        .initialBalance(Money.of(BigDecimal("1500.00"), Currency.COP))
        .description("Fondo de emergencia")
        .build()

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
    fun `given no accounts, when getting all, then emits success with empty list`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)

        val result = mutableListOf<Outcome<List<Account>>>()
        repository.getAll().collect { result.add(it) }

        assertEquals(1, result.size)
        assertTrue(result.first() is Outcome.Success)
        assertTrue((result.first() as Outcome.Success).value.isEmpty())
    }

    @Test
    fun `given one account added, when getting all, then emits success with that account`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val savings = account("Ahorros")
        repository.add(savings)

        val result = mutableListOf<Outcome<List<Account>>>()
        repository.getAll().collect { result.add(it) }

        assertEquals(1, result.size)
        val accounts = (result.first() as Outcome.Success).value
        assertEquals(1, accounts.size)
        assertEquals(savings.id, accounts.first().id)
    }

    @Test
    fun `given multiple accounts added, when getting all, then emits all accounts ordered by id ascending`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val checking = account("Corriente")
        val savings = account("Ahorros")
        repository.add(checking)
        repository.add(savings)

        val result = mutableListOf<Outcome<List<Account>>>()
        repository.getAll().collect { result.add(it) }

        assertEquals(1, result.size)
        val accounts = (result.first() as Outcome.Success).value
        assertEquals(2, accounts.size)
        assertTrue(accounts[0].id <= accounts[1].id)
    }

    @Test
    fun `given a store crypto failure, when getting all, then emits failure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))
        val repository = VaultAccountRepository(store, json)

        val result = mutableListOf<Outcome<List<Account>>>()
        repository.getAll().collect { result.add(it) }

        assertEquals(1, result.size)
        assertTrue(result.first() is Outcome.Failure)
    }

    @Test
    fun `given an account exists, when findById is called with its id, then returns the account`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val savings = account("Ahorros")
        repository.add(savings)

        val result = repository.findById(savings.id)

        assertTrue(result is Outcome.Success)
        assertEquals(savings.id, (result as Outcome.Success).value.id)
    }

    @Test
    fun `given no account matches the id, when findById is called, then returns NotFound`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)

        val result = repository.findById("non-existent-id")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.NotFound)
    }

    @Test
    fun `given the store returns a failure, when findById is called, then returns StorageFailure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))
        val repository = VaultAccountRepository(store, json)

        val result = repository.findById("any-id")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is AccountLookupError.StorageFailure)
    }

    @Test
    fun `given one account added, when getting all, then all fields round-trip correctly`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val savings = account("Ahorros")
        repository.add(savings)

        val result = mutableListOf<Outcome<List<Account>>>()
        repository.getAll().collect { result.add(it) }

        val restored = (result.first() as Outcome.Success).value.first()
        assertEquals(savings.id, restored.id)
        assertEquals(savings.name, restored.name)
        assertEquals(savings.initialBalance, restored.initialBalance)
        assertEquals(savings.type, restored.type)
        assertEquals(savings.description, restored.description)
        assertEquals(savings.createdAt, restored.createdAt)
    }

    @Test
    fun `given include incomes false, when finding by id, then returns account with empty incomes`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val incomeRepository = VaultIncomeRepository(store, json)
        val savings = account("Ahorros")
        repository.add(savings)
        val income = Income.restore(
            id = "inc-1",
            accountId = savings.id,
            amount = Money.of(BigDecimal("300.00"), Currency.COP),
            date = LocalDate.parse("2026-08-28"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-08-28T10:00:00Z")
        )
        incomeRepository.add(income)

        val result = repository.findById(savings.id, AccountCriteria(includeIncomes = false))

        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.incomes.isEmpty())
    }

    @Test
    fun `given account with incomes, when finding by id with criteria, then returns account with incomes`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultAccountRepository(store, json)
        val incomeRepository = VaultIncomeRepository(store, json)
        val savings = account("Ahorros")
        repository.add(savings)
        val income = Income.restore(
            id = "inc-1",
            accountId = savings.id,
            amount = Money.of(BigDecimal("300.00"), Currency.COP),
            date = LocalDate.parse("2026-08-28"),
            categoryId = "cat-1",
            description = "",
            createdAt = Instant.parse("2026-08-28T10:00:00Z")
        )
        incomeRepository.add(income)

        val result = repository.findById(savings.id, AccountCriteria(includeIncomes = true))

        assertTrue(result is Outcome.Success)
        val loadedAccount = (result as Outcome.Success).value
        assertEquals(1, loadedAccount.incomes.size)
        assertEquals("inc-1", loadedAccount.incomes.first().id)
        assertEquals(0, loadedAccount.incomes.first().amount.amount.compareTo(BigDecimal("300.00")))
    }
}
