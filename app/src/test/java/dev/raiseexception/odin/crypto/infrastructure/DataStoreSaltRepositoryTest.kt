package dev.raiseexception.odin.crypto.infrastructure

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val SALT_SIZE = 16

class DataStoreSaltRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createRepository(): DataStoreSaltRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { temporaryFolder.newFile("test_salt.preferences_pb") }
        )
        return DataStoreSaltRepository(dataStore)
    }

    @Test
    fun `given no salt stored, when exists called, then returns false`() = runTest {
        val repository = createRepository()

        val result = repository.exists()

        assertFalse(result)
    }

    @Test
    fun `given no salt stored, when get called, then returns SaltNotFound`() = runTest {
        val repository = createRepository()

        val result = repository.get()

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.SaltNotFound)
    }

    @Test
    fun `given a salt, when saved and retrieved, then returns the same bytes`() = runTest {
        val repository = createRepository()
        val salt = ByteArray(SALT_SIZE) { it.toByte() }

        val saveResult = repository.save(salt)
        val result = repository.get()

        assertTrue(saveResult is Outcome.Success)
        assertTrue(result is Outcome.Success)
        assertArrayEquals(salt, (result as Outcome.Success).value)
    }

    @Test
    fun `given a salt already stored, when exists called, then returns true`() = runTest {
        val repository = createRepository()
        val salt = ByteArray(SALT_SIZE) { it.toByte() }
        repository.save(salt)

        val result = repository.exists()

        assertTrue(result)
    }

    @Test
    fun `given a salt stored, when delete called, then exists returns false`() = runTest {
        val repository = createRepository()
        val salt = ByteArray(SALT_SIZE) { it.toByte() }
        repository.save(salt)

        repository.delete()

        assertFalse(repository.exists())
        assertTrue(repository.get() is Outcome.Failure)
    }
}
