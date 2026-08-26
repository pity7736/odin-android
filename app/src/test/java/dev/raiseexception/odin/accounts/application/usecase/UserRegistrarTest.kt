package dev.raiseexception.odin.accounts.application.usecase

import dev.raiseexception.odin.accounts.domain.RegistrationError
import dev.raiseexception.odin.accounts.domain.repository.UserRepository
import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.DerivedKeys
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.shared.domain.Outcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
private const val MAX_PASSWORD_LENGTH = 100
private const val OVER_MAX_PASSWORD_LENGTH = 101

class UserRegistrarTest {

    private val vaultCrypto = mockk<VaultCrypto>()
    private val userRepository = mockk<UserRepository>()
    private val masterKeyRepository = mockk<MasterKeyRepository>(relaxUnitFun = true)
    private lateinit var registrar: UserRegistrar

    private val salt = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + SALT_OFFSET).toByte() }
    private val encryptionKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + ENCRYPTION_KEY_OFFSET).toByte() }
    private val masterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + MASTER_KEY_OFFSET).toByte() }
    private val wrappedMasterKey = ByteArray(TEST_BYTE_ARRAY_SIZE) { (it + WRAPPED_KEY_OFFSET).toByte() }
    private val derivedKeys = DerivedKeys(authHash = "authHash", encryptionKey = encryptionKey)
    private val validPassword = "validPassword1"
    private val validPasswordConfirmation = "validPassword1"

    @Before
    fun setUp() {
        registrar = UserRegistrar(vaultCrypto, userRepository, masterKeyRepository, UnconfinedTestDispatcher())
    }

    @Test
    fun `given valid password, when registering, then completes full crypto flow and returns user`() = runTest {
        coEvery { userRepository.exists() } returns false
        every { vaultCrypto.generateSalt() } returns salt
        every { vaultCrypto.deriveKeys(validPassword, salt) } returns Outcome.Success(derivedKeys)
        every { vaultCrypto.generateMasterKey() } returns masterKey
        every { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) } returns Outcome.Success(wrappedMasterKey)
        coEvery { userRepository.add(any()) } returns Outcome.Success(Unit)

        val result = registrar.register(validPassword, validPasswordConfirmation)

        assertTrue(result is Outcome.Success)
        verify { vaultCrypto.generateSalt() }
        verify { vaultCrypto.deriveKeys(validPassword, salt) }
        verify { vaultCrypto.generateMasterKey() }
        verify { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) }
        coVerify { userRepository.add(any()) }
        verify { masterKeyRepository.store(masterKey) }
    }

    @Test
    fun `given valid password, when registering, then user contains id salt and wrapped key`() = runTest {
        coEvery { userRepository.exists() } returns false
        every { vaultCrypto.generateSalt() } returns salt
        every { vaultCrypto.deriveKeys(validPassword, salt) } returns Outcome.Success(derivedKeys)
        every { vaultCrypto.generateMasterKey() } returns masterKey
        every { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) } returns Outcome.Success(wrappedMasterKey)
        coEvery { userRepository.add(any()) } returns Outcome.Success(Unit)

        val result = registrar.register(validPassword, validPasswordConfirmation)

        assertTrue(result is Outcome.Success)
        val user = (result as Outcome.Success).value
        assertTrue(user.id.isNotEmpty())
        assertTrue(user.salt.contentEquals(salt))
        assertTrue(user.wrappedMasterKey.contentEquals(wrappedMasterKey))
    }

    @Test
    fun `given password of exactly 12 chars, when registering, then succeeds`() = runTest {
        val shortPassword = "123456789012"
        coEvery { userRepository.exists() } returns false
        every { vaultCrypto.generateSalt() } returns salt
        every { vaultCrypto.deriveKeys(shortPassword, salt) } returns Outcome.Success(derivedKeys)
        every { vaultCrypto.generateMasterKey() } returns masterKey
        every { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) } returns Outcome.Success(wrappedMasterKey)
        coEvery { userRepository.add(any()) } returns Outcome.Success(Unit)

        val result = registrar.register(shortPassword, shortPassword)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given password of exactly 100 chars, when registering, then succeeds`() = runTest {
        val longPassword = "a".repeat(MAX_PASSWORD_LENGTH)
        coEvery { userRepository.exists() } returns false
        every { vaultCrypto.generateSalt() } returns salt
        every { vaultCrypto.deriveKeys(longPassword, salt) } returns Outcome.Success(derivedKeys)
        every { vaultCrypto.generateMasterKey() } returns masterKey
        every { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) } returns Outcome.Success(wrappedMasterKey)
        coEvery { userRepository.add(any()) } returns Outcome.Success(Unit)

        val result = registrar.register(longPassword, longPassword)

        assertTrue(result is Outcome.Success)
    }

    @Test
    fun `given password shorter than 12 chars, when registering, then returns invalid password`() = runTest {
        coEvery { userRepository.exists() } returns false

        val result = registrar.register("short", "short")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given password longer than 100 chars, when registering, then returns invalid password`() = runTest {
        val tooLong = "a".repeat(OVER_MAX_PASSWORD_LENGTH)
        coEvery { userRepository.exists() } returns false

        val result = registrar.register(tooLong, tooLong)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given empty password, when registering, then returns invalid password`() = runTest {
        coEvery { userRepository.exists() } returns false

        val result = registrar.register("", "")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.InvalidPassword)
    }

    @Test
    fun `given mismatched passwords, when registering, then returns passwords do not match`() = runTest {
        coEvery { userRepository.exists() } returns false

        val result = registrar.register(validPassword, "differentPassword")

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.PasswordsDoNotMatch)
    }

    @Test
    fun `given deriveKeys fails, when registering, then returns crypto failure`() = runTest {
        coEvery { userRepository.exists() } returns false
        every { vaultCrypto.generateSalt() } returns salt
        every { vaultCrypto.deriveKeys(validPassword, salt) } returns Outcome.Failure(
            CryptoError.InvalidSalt()
        )

        val result = registrar.register(validPassword, validPasswordConfirmation)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.CryptoFailure)
    }

    @Test
    fun `given wrapMasterKey fails, when registering, then returns crypto failure`() = runTest {
        coEvery { userRepository.exists() } returns false
        every { vaultCrypto.generateSalt() } returns salt
        every { vaultCrypto.deriveKeys(validPassword, salt) } returns Outcome.Success(derivedKeys)
        every { vaultCrypto.generateMasterKey() } returns masterKey
        every { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) } returns Outcome.Failure(
            CryptoError.InvalidKeySize()
        )

        val result = registrar.register(validPassword, validPasswordConfirmation)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.CryptoFailure)
    }

    @Test
    fun `given crypto succeeds but add fails, when registering, then returns storage failure`() = runTest {
        coEvery { userRepository.exists() } returns false
        every { vaultCrypto.generateSalt() } returns salt
        every { vaultCrypto.deriveKeys(validPassword, salt) } returns Outcome.Success(derivedKeys)
        every { vaultCrypto.generateMasterKey() } returns masterKey
        every { vaultCrypto.wrapMasterKey(masterKey, encryptionKey) } returns Outcome.Success(wrappedMasterKey)
        coEvery { userRepository.add(any()) } returns Outcome.Failure(
            RegistrationError.StorageFailure(
                internalMessage = "Storage failed",
                externalMessage = "Algo salio mal"
            )
        )

        val result = registrar.register(validPassword, validPasswordConfirmation)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.StorageFailure)
    }

    @Test
    fun `given user already exists, when registering, then returns already registered`() = runTest {
        coEvery { userRepository.exists() } returns true

        val result = registrar.register(validPassword, validPasswordConfirmation)

        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is RegistrationError.AlreadyRegistered)
    }

    @Test
    fun `given user already exists, when registering, then already registered has correct message`() = runTest {
        coEvery { userRepository.exists() } returns true

        val result = registrar.register(validPassword, validPasswordConfirmation)

        val error = (result as Outcome.Failure).error as RegistrationError.AlreadyRegistered
        val expectedMessage = "Ya existe un usuario en este dispositivo"
        assertTrue(error.externalMessage == expectedMessage)
    }
}
