package dev.raiseexception.odin.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.shared.domain.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ENCRYPTION_KEY_SIZE = 32

@RunWith(RobolectricTestRunner::class)
class DatabaseProviderTest {

    private lateinit var context: Context
    private lateinit var databaseProvider: DatabaseProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseProvider = DatabaseProvider(context, openHelperFactoryProvider = { null })
    }

    @Test(expected = IllegalStateException::class)
    fun `given unlock not called, when requireDatabase called, then throws IllegalStateException`() {
        databaseProvider.requireDatabase()
    }

    @Test
    fun `given a valid encryption key, when unlock called, then returns success`() {
        val encryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }

        val result = databaseProvider.unlock(encryptionKey)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given a valid encryption key, when unlock called, then requireDatabase returns a usable database`() {
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
}
