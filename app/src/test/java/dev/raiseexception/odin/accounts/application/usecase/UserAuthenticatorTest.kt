package dev.raiseexception.odin.accounts.application.usecase

import dev.raiseexception.odin.accounts.domain.LoginError
import dev.raiseexception.odin.accounts.domain.model.User
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.DerivedKeys
import dev.raiseexception.odin.crypto.domain.SensitivePassword
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.crypto.domain.repository.SaltRepository
import dev.raiseexception.odin.shared.domain.Outcome
import dev.raiseexception.odin.shared.domain.VaultUnlocker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TEST_BYTE_ARRAY_SIZE = 16
private const val SALT_OFFSET = 0
private const val ENCRYPTION_KEY_OFFSET = 1
private const val MASTER_KEY_OFFSET = 2
private const val WRAPPED_KEY_OFFSET = 3

@OptIn(ExperimentalCoroutinesApi::class)
class UserAuthenticatorTest {

    private val vaultCrypto = mockk<VaultCrypto>()
    private val userRepository = mockk<UserRepository>()
    private val masterKeyRepository = mockk<MasterKeyRepository>(relaxUnitFun = true)
    private val saltRepository = mockk<SaltRepository>()
    private val vaultUnlocker = mockk<VaultUnlocker>()
    private lateinit var authenticator: UserAuthenticator

    private val salt = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + SALT_OFFSET).toByte() }
    private val encryptionKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + ENCRYPTION_KEY_OFFSET).toByte() }
    private val masterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + MASTER_KEY_OFFSET).toByte() }
    private val wrappedMasterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + WRAPPED_KEY_OFFSET).toByte() }
    private val derivedKeys = DerivedKeys(authHash = "authHash", encryptionKey = encryptionKey)
    private val storedUser = User(id = "user-1", wrappedMasterKey = wrappedMasterKey)

    @Before
    fun setUp() {
        authenticator = UserAuthenticator(
            vaultCrypto,
            userRepository,
            masterKeyRepository,
            saltRepository,
            vaultUnlocker,
            UnconfinedTestDispatcher()
        )
    }

    @Test
    fun `given a correct password, when authenticating, then stores the master key and returns the user`() =
        runTest {
            stubSuccessfulAuthentication()

            val result = authenticator.authenticate(sensitivePassword("validPassword1"))

            assertTrue(result is Outcome.Success)
            assertTrue((result as Outcome.Success).value == storedUser)
            verify { vaultCrypto.unwrapMasterKey(wrappedMasterKey, encryptionKey) }
            verify { masterKeyRepository.store(masterKey) }
        }

    @Test
    fun `given valid password, when authenticate called, then salt read from SaltRepository`() = runTest {
        stubSuccessfulAuthentication()

        authenticator.authenticate(sensitivePassword("validPassword1"))

        coVerify { saltRepository.get() }
    }

    @Test
    fun `given valid password, when authenticate called, then vault unlocked with derived encryptionKey`() = runTest {
        stubSuccessfulAuthentication()

        authenticator.authenticate(sensitivePassword("validPassword1"))

        verify { vaultUnlocker.unlock(encryptionKey) }
    }

    @Test
    fun `given valid password, when authenticate called, then wrappedMasterKey read from UserRepository`() = runTest {
        stubSuccessfulAuthentication()

        authenticator.authenticate(sensitivePassword("validPassword1"))

        coVerify { userRepository.get() }
    }

    @Test
    fun `given no salt stored, when authenticate called, then returns UserNotFound`() = runTest {
        coEvery { saltRepository.get() } returns null

        val result = authenticator.authenticate(sensitivePassword("validPassword1"))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is LoginError.UserNotFound)
    }

    @Test
    fun `given an incorrect password, when authenticating, then returns invalid credentials and stores nothing`() =
        runTest {
            coEvery { saltRepository.get() } returns salt
            every { vaultCrypto.deriveKeys(any(), eq(salt)) } returns Outcome.Success(derivedKeys)
            every { vaultUnlocker.unlock(encryptionKey) } returns Outcome.Success(Unit)
            coEvery { userRepository.get() } returns Outcome.Success(storedUser)
            every { vaultCrypto.unwrapMasterKey(wrappedMasterKey, encryptionKey) } returns Outcome.Failure(
                CryptoError.DecryptionFailed()
            )

            val result = authenticator.authenticate(sensitivePassword("validPassword1"))

            assertTrue(result is Outcome.Failure)
            assertTrue((result as Outcome.Failure).error is LoginError.InvalidCredentials)
            verify(exactly = 0) { masterKeyRepository.store(any()) }
        }

    @Test
    fun `given a blank password, when authenticating, then returns empty password and never calls the crypto`() =
        runTest {
            val result = authenticator.authenticate(sensitivePassword("   "))

            assertTrue(result is Outcome.Failure)
            assertTrue((result as Outcome.Failure).error is LoginError.EmptyPassword)
            verify(exactly = 0) { vaultCrypto.deriveKeys(any(), any()) }
            verify(exactly = 0) { vaultCrypto.unwrapMasterKey(any(), any()) }
        }

    @Test
    fun `given key derivation fails, when authenticating, then returns crypto failure`() = runTest {
        coEvery { saltRepository.get() } returns salt
        every { vaultCrypto.deriveKeys(any(), eq(salt)) } returns Outcome.Failure(CryptoError.InvalidSalt())

        val result = authenticator.authenticate(sensitivePassword("validPassword1"))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is LoginError.CryptoFailure)
    }

    @Test
    fun `given a non tag unwrap failure, when authenticating, then returns crypto failure`() =
        runTest {
            coEvery { saltRepository.get() } returns salt
            every { vaultCrypto.deriveKeys(any(), eq(salt)) } returns Outcome.Success(derivedKeys)
            every { vaultUnlocker.unlock(encryptionKey) } returns Outcome.Success(Unit)
            coEvery { userRepository.get() } returns Outcome.Success(storedUser)
            every { vaultCrypto.unwrapMasterKey(wrappedMasterKey, encryptionKey) } returns Outcome.Failure(
                CryptoError.InvalidKeySize()
            )

            val result = authenticator.authenticate(sensitivePassword("validPassword1"))

            assertTrue(result is Outcome.Failure)
            assertTrue((result as Outcome.Failure).error is LoginError.CryptoFailure)
        }

    @Test
    fun `given no registered user, when authenticating, then returns user not found`() = runTest {
        coEvery { saltRepository.get() } returns salt
        every { vaultCrypto.deriveKeys(any(), eq(salt)) } returns Outcome.Success(derivedKeys)
        every { vaultUnlocker.unlock(encryptionKey) } returns Outcome.Success(Unit)
        coEvery { userRepository.get() } returns Outcome.Failure(
            LoginError.UserNotFound(
                internalMessage = "No user stored on this device",
                externalMessage = "Algo salió mal. Intente de nuevo más tarde"
            )
        )

        val result = authenticator.authenticate(sensitivePassword("validPassword1"))

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is LoginError.UserNotFound)
    }

    @Test
    fun `given a valid authentication, when completed, then password is wiped`() = runTest {
        stubSuccessfulAuthentication()
        val password = sensitivePassword("validPassword1")

        authenticator.authenticate(password)

        assertTrue(password.isBlank())
    }

    @Test
    fun `given a blank password, when authenticating, then password is wiped`() = runTest {
        val password = sensitivePassword("   ")

        authenticator.authenticate(password)

        assertTrue(password.isBlank())
    }

    private fun stubSuccessfulAuthentication() {
        coEvery { saltRepository.get() } returns salt
        every { vaultCrypto.deriveKeys(any(), eq(salt)) } returns Outcome.Success(derivedKeys)
        every { vaultUnlocker.unlock(encryptionKey) } returns Outcome.Success(Unit)
        coEvery { userRepository.get() } returns Outcome.Success(storedUser)
        every { vaultCrypto.unwrapMasterKey(wrappedMasterKey, encryptionKey) } returns Outcome.Success(masterKey)
    }

    private fun sensitivePassword(raw: String) = SensitivePassword(raw.toCharArray())
}
