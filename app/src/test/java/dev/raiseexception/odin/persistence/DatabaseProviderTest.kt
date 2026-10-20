package dev.raiseexception.odin.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounts.infrastructure.repository.UserEntity
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ENCRYPTION_KEY_SIZE = 32
private const val WRAPPED_MASTER_KEY_SIZE = 8
private const val DATABASE_NAME = "odin_db"

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DatabaseProviderTest {

    private lateinit var context: Context
    private lateinit var databaseProvider: DatabaseProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseProvider = DatabaseProvider(context, UnconfinedTestDispatcher(), openHelperFactoryProvider = { null })
    }

    @Test(expected = IllegalStateException::class)
    fun `given unlock not called, when requireDatabase called, then throws IllegalStateException`() {
        databaseProvider.requireDatabase()
    }

    @Test
    fun `given a valid encryption key, when unlock called, then returns success`() = runTest {
        val encryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }

        val result = databaseProvider.unlock(encryptionKey)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid encryption key, when unlock called, then requireDatabase returns a usable database`() = runTest {
        val encryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }
        databaseProvider.unlock(encryptionKey)

        val database = databaseProvider.requireDatabase()

        assertNotNull(database)
    }

    @Test
    fun `given a valid encryption key, when unlock called, then database can read and write`() = runTest {
        val encryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }
        databaseProvider.unlock(encryptionKey)

        val database = databaseProvider.requireDatabase()
        val exists = database.userDao().exists()

        assertTrue(!exists)
    }

    @Test(expected = IllegalStateException::class)
    fun `given an unlocked database, when discard called, then requireDatabase throws`() = runTest {
        val encryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }
        databaseProvider.unlock(encryptionKey)

        databaseProvider.discard()

        databaseProvider.requireDatabase()
    }

    @Test
    fun `given an unlocked database, when discard called, then the database file no longer exists`() = runTest {
        val encryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }
        databaseProvider.unlock(encryptionKey)

        val result = databaseProvider.discard()

        assertTrue(result is Outcome.Success)
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
    }

    @Test
    fun `given a row written and the vault discarded, when unlock called again, then the database is empty`() =
        runTest {
            val firstEncryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }
            val secondEncryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { (it + 1).toByte() }
            databaseProvider.unlock(firstEncryptionKey)
            databaseProvider.requireDatabase().userDao().insert(
                UserEntity(id = "user-1", wrappedMasterKey = ByteArray(WRAPPED_MASTER_KEY_SIZE))
            )
            databaseProvider.discard()

            databaseProvider.unlock(secondEncryptionKey)

            assertFalse(databaseProvider.requireDatabase().userDao().exists())
        }

    @Test
    fun `given nothing unlocked and no file, when discard called, then returns success`() = runTest {
        val result = databaseProvider.discard()

        assertTrue(result is Outcome.Success)
    }
}
