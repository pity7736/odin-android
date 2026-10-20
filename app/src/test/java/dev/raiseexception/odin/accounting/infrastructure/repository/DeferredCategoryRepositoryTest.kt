package dev.raiseexception.odin.accounting.infrastructure.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.persistence.DatabaseProvider
import dev.raiseexception.odin.testutil.CategoryBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val ENCRYPTION_KEY_SIZE = 32

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeferredCategoryRepositoryTest {

    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var repository: DeferredCategoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseProvider = DatabaseProvider(context, UnconfinedTestDispatcher(), openHelperFactoryProvider = { null })
        repository = DeferredCategoryRepository(databaseProvider)
    }

    @Test
    fun `given the vault discarded and unlocked again, when adding, then the record is stored in the new database`() =
        runTest {
            val firstEncryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { it.toByte() }
            val secondEncryptionKey = ByteArray(ENCRYPTION_KEY_SIZE) { (it + 1).toByte() }
            databaseProvider.unlock(firstEncryptionKey)
            repository.existsByNameAndType("Transferencia", CategoryType.TRANSFER)
            databaseProvider.discard()
            databaseProvider.unlock(secondEncryptionKey)

            repository.add(CategoryBuilder().name("Transferencia").type(CategoryType.TRANSFER).build())

            assertTrue(
                databaseProvider.requireDatabase().categoryDao().existsByNameAndType(
                    "Transferencia",
                    CategoryType.TRANSFER.name
                )
            )
        }
}
