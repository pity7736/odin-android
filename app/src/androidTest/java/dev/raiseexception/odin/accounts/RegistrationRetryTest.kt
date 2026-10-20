package dev.raiseexception.odin.accounts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.raiseexception.odin.accounting.application.usecase.CategoryCreator
import dev.raiseexception.odin.accounting.domain.model.CategoryType
import dev.raiseexception.odin.accounting.infrastructure.repository.DeferredCategoryRepository
import dev.raiseexception.odin.accounts.application.usecase.UserAuthenticator
import dev.raiseexception.odin.accounts.application.usecase.UserRegistrar
import dev.raiseexception.odin.accounts.infrastructure.repository.DeferredUserRepository
import dev.raiseexception.odin.crypto.domain.SensitivePassword
import dev.raiseexception.odin.crypto.infrastructure.BouncyCastleVaultCrypto
import dev.raiseexception.odin.crypto.infrastructure.DataStoreSaltRepository
import dev.raiseexception.odin.crypto.infrastructure.InMemoryMasterKeyRepository
import dev.raiseexception.odin.persistence.DatabaseProvider
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.StorageError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

private const val DATABASE_NAME = "odin_db"
private const val FAILED_ATTEMPT_PASSWORD = "firstAttemptPassword"
private const val RETRY_PASSWORD = "retryAttemptPassword"
private const val TRANSFER_CATEGORY_NAME = "Transferencia"

@RunWith(AndroidJUnit4::class)
class RegistrationRetryTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val vaultCrypto = BouncyCastleVaultCrypto()
    private val saltRepository = DataStoreSaltRepository(saltDataStore)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        this.clearDeviceState()
    }

    @After
    fun tearDown() {
        this.clearDeviceState()
    }

    @Test
    fun given_a_failed_registration_when_retrying_without_restarting_then_login_with_the_retry_password_succeeds() =
        runBlocking {
            val databaseProvider = DatabaseProvider(this@RegistrationRetryTest.context, Dispatchers.IO)
            val registrar = this@RegistrationRetryTest.registrar(databaseProvider, failingAttempts = 1)
            registrar.register(password(FAILED_ATTEMPT_PASSWORD), password(FAILED_ATTEMPT_PASSWORD))

            val retryOutcome = registrar.register(password(RETRY_PASSWORD), password(RETRY_PASSWORD))

            assertTrue(retryOutcome is Outcome.Success)
            this@RegistrationRetryTest.assertColdStartLoginSucceeds()
        }

    @Test
    fun given_a_failed_registration_when_retrying_after_restarting_then_login_with_the_retry_password_succeeds() =
        runBlocking {
            val failedAttemptDatabaseProvider = DatabaseProvider(this@RegistrationRetryTest.context, Dispatchers.IO)
            this@RegistrationRetryTest.registrar(failedAttemptDatabaseProvider, failingAttempts = 1)
                .register(password(FAILED_ATTEMPT_PASSWORD), password(FAILED_ATTEMPT_PASSWORD))
            val retryDatabaseProvider = DatabaseProvider(this@RegistrationRetryTest.context, Dispatchers.IO)
            val retryRegistrar = this@RegistrationRetryTest.registrar(retryDatabaseProvider, failingAttempts = 0)

            val retryOutcome = retryRegistrar.register(password(RETRY_PASSWORD), password(RETRY_PASSWORD))

            assertTrue(retryOutcome is Outcome.Success)
            this@RegistrationRetryTest.assertColdStartLoginSucceeds()
        }

    private fun registrar(databaseProvider: DatabaseProvider, failingAttempts: Int): UserRegistrar {
        val categoryCreator = CategoryCreator(DeferredCategoryRepository(databaseProvider))
        val transferCategoryStep = TransferCategoryStep(categoryCreator, failingAttempts)
        return UserRegistrar(
            this.vaultCrypto,
            DeferredUserRepository(databaseProvider),
            InMemoryMasterKeyRepository(),
            this.saltRepository,
            databaseProvider,
            postRegistration = { transferCategoryStep.run() }
        )
    }

    private suspend fun assertColdStartLoginSucceeds() {
        val coldStartDatabaseProvider = DatabaseProvider(this.context, Dispatchers.IO)
        val authenticator = UserAuthenticator(
            this.vaultCrypto,
            DeferredUserRepository(coldStartDatabaseProvider),
            InMemoryMasterKeyRepository(),
            DataStoreSaltRepository(saltDataStore),
            coldStartDatabaseProvider
        )
        val loginOutcome = authenticator.authenticate(password(RETRY_PASSWORD))
        val categoriesOutcome = DeferredCategoryRepository(coldStartDatabaseProvider)
            .findByType(CategoryType.TRANSFER)
            .first()
        assertTrue(loginOutcome is Outcome.Success)
        assertTrue(categoriesOutcome is Outcome.Success)
        assertTrue(
            (categoriesOutcome as Outcome.Success).value.any { category -> category.name == TRANSFER_CATEGORY_NAME }
        )
        coldStartDatabaseProvider.discard()
    }

    private fun clearDeviceState() {
        this.context.deleteDatabase(DATABASE_NAME)
        runBlocking { saltDataStore.edit { preferences -> preferences.clear() } }
    }

    private fun password(raw: String) = SensitivePassword(raw.toCharArray())

    companion object {
        private val saltDataStore: DataStore<Preferences> by lazy {
            PreferenceDataStoreFactory.create(
                produceFile = {
                    InstrumentationRegistry.getInstrumentation().targetContext
                        .preferencesDataStoreFile("registration_retry_test_salt")
                }
            )
        }
    }
}

private class TransferCategoryStep(
    private val categoryCreator: CategoryCreator,
    private val failingAttempts: Int
) {

    private val attempts = AtomicInteger(0)

    suspend fun run(): Outcome<Unit> {
        if (this.attempts.getAndIncrement() < this.failingAttempts) {
            return Outcome.Failure(StorageError(internalMessage = "Simulated Transfer category failure"))
        }
        return when (
            val creationOutcome = this.categoryCreator.createSystem(
                name = TRANSFER_CATEGORY_NAME,
                type = CategoryType.TRANSFER,
                description = "",
                color = "#607D8B"
            )
        ) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> Outcome.Failure(creationOutcome.error)
        }
    }
}
