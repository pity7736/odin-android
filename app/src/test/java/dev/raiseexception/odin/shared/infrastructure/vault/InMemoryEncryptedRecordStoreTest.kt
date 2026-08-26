package dev.raiseexception.odin.shared.infrastructure.vault

import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MASTER_KEY_SIZE = 32

private class FakeMasterKeyRepository(private var masterKey: ByteArray?) : MasterKeyRepository {
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

class InMemoryEncryptedRecordStoreTest {

    private val vaultCrypto = BouncyCastleVaultCrypto()
    private val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
    private val plaintext = "hello vault".toByteArray()

    private fun storeWith(masterKeyRepository: MasterKeyRepository) = InMemoryEncryptedRecordStore(
        vaultCrypto = vaultCrypto,
        masterKeyRepository = masterKeyRepository,
        cpuDispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun `given a saved record, when inspecting the held blob, then it is not the plaintext`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))

        store.save("record-1", plaintext)

        val heldBlob = store.entries.getValue("record-1")
        assertFalse(heldBlob.contentEquals(plaintext))
    }

    @Test
    fun `given a saved record, when reading all, then the plaintext round-trips`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(masterKey))

        store.save("record-1", plaintext)
        val result = store.readAll()

        assertTrue(result is Outcome.Success)
        val records = (result as Outcome.Success).value
        assertEquals(1, records.size)
        assertEquals("record-1", records.first().id)
        assertTrue(records.first().data.contentEquals(plaintext))
    }

    @Test
    fun `given no master key, when saving, then returns failure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))

        val result = store.save("record-1", plaintext)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.MasterKeyNotFound)
    }

    @Test
    fun `given no master key, when reading all, then returns failure`() = runTest {
        val store = storeWith(FakeMasterKeyRepository(null))

        val result = store.readAll()

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.MasterKeyNotFound)
    }
}
