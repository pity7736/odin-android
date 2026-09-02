package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.ExpenseCreationError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Expense
import dev.raiseexception.odin.accounting.domain.model.Money
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.infrastructure.vault.InMemoryEncryptedRecordStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private const val MASTER_KEY_SIZE = 32

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VaultExpenseRepositoryTest {

    private val vaultCrypto = BouncyCastleVaultCrypto()
    private val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
    private val json = Json

    private fun storeWith(masterKeyRepository: MasterKeyRepository) = InMemoryEncryptedRecordStore(
        vaultCrypto = vaultCrypto,
        masterKeyRepository = masterKeyRepository,
        cpuDispatcher = UnconfinedTestDispatcher()
    )

    private fun expense(accountId: String = "acc-1") = Expense.restore(
        id = "exp-1",
        accountId = accountId,
        amount = Money.of(BigDecimal("500.00"), Currency.COP),
        date = LocalDate.parse("2026-08-28"),
        categoryId = "cat-1",
        description = "Mercado",
        createdAt = Instant.parse("2026-08-28T10:00:00Z")
    )

    @Test
    fun `given a valid expense, when adding, then expense is persisted`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultExpenseRepository(store, json)
        val testExpense = expense()

        val result = repository.add(testExpense)

        assertTrue(result is Outcome.Success)
        val storedRecords = (store.readAll() as Outcome.Success).value
        assertEquals(1, storedRecords.size)
    }

    @Test
    fun `given crypto failure, when adding expense, then returns crypto failure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))
        val repository = VaultExpenseRepository(store, json)

        val result = repository.add(expense())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is ExpenseCreationError.CryptoFailure)
    }
}
