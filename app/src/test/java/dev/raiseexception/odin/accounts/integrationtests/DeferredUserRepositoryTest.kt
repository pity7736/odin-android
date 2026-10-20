package dev.raiseexception.odin.accounts.integrationtests

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.infrastructure.repository.DeferredUserRepository
import dev.raiseexception.odin.persistence.DatabaseProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ENCRYPTION_KEY_SIZE = 32
private const val WRAPPED_MASTER_KEY_SIZE = 8

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeferredUserRepositoryTest {

    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var repository: DeferredUserRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseProvider = DatabaseProvider(context, UnconfinedTestDispatcher(), openHelperFactoryProvider = { null })
        repository = DeferredUserRepository(databaseProvider)
    }

    @Test
    fun `given the vault discarded and unlocked again, when adding, then the record is stored in the new database`() =
        runTest {
            val firstEncryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }
            val secondEncryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { (it + 1).toByte() }
            databaseProvider.unlock(firstEncryptionKey)
            repository.get()
            databaseProvider.discard()
            databaseProvider.unlock(secondEncryptionKey)

            repository.add(User(id = "user-1", wrappedMasterKey = ByteArray(WRAPPED_MASTER_KEY_SIZE)))

            assertTrue(databaseProvider.requireDatabase().userDao().exists())
        }
}
