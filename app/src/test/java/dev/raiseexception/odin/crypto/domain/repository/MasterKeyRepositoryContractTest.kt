package dev.raiseexception.odin.crypto.domain.repository

import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.shared.domain.Outcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MASTER_KEY_SIZE = 32

abstract class MasterKeyRepositoryContractTest {

    abstract fun createRepository(): MasterKeyRepository

    private val repository by lazy { createRepository() }

    @Test
    fun `given a master key, when storing it, then it can be retrieved`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        repository.store(masterKey)
        val result = repository.get()
        assertTrue(result is Outcome.Success)
        assertArrayEquals(masterKey, (result as Outcome.Success).value)
    }

    @Test
    fun `given no master key stored, when retrieving, then returns master key not found error`() {
        val result = repository.get()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.MasterKeyNotFound)
    }

    @Test
    fun `given a stored master key, when clearing, then retrieving returns master key not found error`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        repository.store(masterKey)
        repository.clear()
        val result = repository.get()
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.MasterKeyNotFound)
    }

    @Test
    fun `given a master key, when storing a different one, then the new one replaces the old`() {
        val firstKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val secondKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        repository.store(firstKey)
        repository.store(secondKey)
        val result = repository.get()
        assertTrue(result is Outcome.Success)
        assertArrayEquals(secondKey, (result as Outcome.Success).value)
    }
}
