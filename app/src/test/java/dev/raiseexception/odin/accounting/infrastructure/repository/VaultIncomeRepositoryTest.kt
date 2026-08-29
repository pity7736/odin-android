package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.accounting.domain.IncomeCreationError
import dev.raiseexception.odin.accounting.domain.model.Currency
import dev.raiseexception.odin.accounting.domain.model.Income
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
class VaultIncomeRepositoryTest {

    private val vaultCrypto = BouncyCastleVaultCrypto()
    private val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
    private val json = Json

    private fun storeWith(masterKeyRepository: MasterKeyRepository) = InMemoryEncryptedRecordStore(
        vaultCrypto = vaultCrypto,
        masterKeyRepository = masterKeyRepository,
        cpuDispatcher = UnconfinedTestDispatcher()
    )

    private fun income(accountId: String = "acc-1") = Income.restore(
        id = "inc-1",
        accountId = accountId,
        amount = Money.of(BigDecimal("500.00"), Currency.COP),
        date = LocalDate.parse("2026-08-28"),
        categoryId = "cat-1",
        description = "Salario",
        createdAt = Instant.parse("2026-08-28T10:00:00Z")
    )

    @Test
    fun `given a valid income, when adding, then income is persisted`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))
        val repository = VaultIncomeRepository(store, json)
        val testIncome = income()

        val result = repository.add(testIncome)

        assertTrue(result is Outcome.Success)
        val storedRecords = (store.readAll() as Outcome.Success).value
        assertEquals(1, storedRecords.size)
    }

    @Test
    fun `given crypto failure, when adding income, then returns crypto failure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))
        val repository = VaultIncomeRepository(store, json)

        val result = repository.add(income())

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is IncomeCreationError.CryptoFailure)
    }
}
