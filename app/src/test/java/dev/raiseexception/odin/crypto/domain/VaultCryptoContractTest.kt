package dev.raiseexception.odin.crypto.domain

import dev.raiseexception.odin.shared.domain.Outcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MASTER_KEY_SIZE = 32
private const val SALT_SIZE = 16
private const val GCM_NONCE_SIZE = 12
private const val GCM_TAG_BYTE_LENGTH = 16
private const val MINIMUM_SEALED_SIZE = GCM_NONCE_SIZE + GCM_TAG_BYTE_LENGTH

abstract class VaultCryptoContractTest {

    abstract fun createVaultCrypto(): VaultCrypto

    private val vaultCrypto by lazy { createVaultCrypto() }

    @Test
    fun `given a valid password and salt, when deriving keys, then returns auth hash and encryption key`() {
        val password = "correcthorsebatterystaple"
        val salt = ByteArray(SALT_SIZE) { it.toByte() }
        val result = vaultCrypto.deriveKeys(password, salt)
        assertTrue(result is Outcome.Success)
        val keys = (result as Outcome.Success).value
        assertTrue(keys.authHash.isNotEmpty())
        assertEquals(MASTER_KEY_SIZE, keys.encryptionKey.size)
    }

    @Test
    fun `given the same password and salt, when deriving keys twice, then returns identical results`() {
        val password = "correcthorsebatterystaple"
        val salt = ByteArray(SALT_SIZE) { it.toByte() }
        val first = vaultCrypto.deriveKeys(password, salt) as Outcome.Success
        val second = vaultCrypto.deriveKeys(password, salt) as Outcome.Success
        assertEquals(first.value, second.value)
    }

    @Test
    fun `given different salts, when deriving keys with the same password, then returns different results`() {
        val password = "correcthorsebatterystaple"
        val saltOne = ByteArray(SALT_SIZE) { it.toByte() }
        val saltTwo = ByteArray(SALT_SIZE) { (it + 1).toByte() }
        val first = vaultCrypto.deriveKeys(password, saltOne) as Outcome.Success
        val second = vaultCrypto.deriveKeys(password, saltTwo) as Outcome.Success
        assertFalse(first.value.authHash == second.value.authHash)
    }

    @Test
    fun `given an empty password, when deriving keys, then returns invalid password error`() {
        val salt = ByteArray(SALT_SIZE) { it.toByte() }
        val result = vaultCrypto.deriveKeys("", salt)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidPassword)
    }

    @Test
    fun `given an empty salt, when deriving keys, then returns invalid salt error`() {
        val result = vaultCrypto.deriveKeys("password", ByteArray(0))
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidSalt)
    }

    @Test
    fun `given a salt shorter than required, when deriving keys, then returns invalid salt error`() {
        val shortSalt = ByteArray(SALT_SIZE - 1) { it.toByte() }
        val result = vaultCrypto.deriveKeys("password", shortSalt)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidSalt)
    }

    @Test
    fun `given a salt longer than required, when deriving keys, then returns invalid salt error`() {
        val longSalt = ByteArray(SALT_SIZE + 1) { it.toByte() }
        val result = vaultCrypto.deriveKeys("password", longSalt)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidSalt)
    }

    @Test
    fun `given nothing, when generating a master key, then returns a 32-byte key`() {
        val masterKey = vaultCrypto.generateMasterKey()
        assertEquals(MASTER_KEY_SIZE, masterKey.size)
    }

    @Test
    fun `given nothing, when generating two master keys, then they are different`() {
        val first = vaultCrypto.generateMasterKey()
        val second = vaultCrypto.generateMasterKey()
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `given a valid master key and encryption key, when wrapping, then returns sealed bytes`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val encryptionKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val result = vaultCrypto.wrapMasterKey(masterKey, encryptionKey)
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isNotEmpty())
    }

    @Test
    fun `given a wrapped master key and correct encryption key, when unwrapping, then recovers the original`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val encryptionKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val wrapped = vaultCrypto.wrapMasterKey(masterKey, encryptionKey) as Outcome.Success
        val unwrapped = vaultCrypto.unwrapMasterKey(wrapped.value, encryptionKey) as Outcome.Success
        assertArrayEquals(masterKey, unwrapped.value)
    }

    @Test
    fun `given the same master key, when wrapping twice, then produces different outputs`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val encryptionKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val first = vaultCrypto.wrapMasterKey(masterKey, encryptionKey) as Outcome.Success
        val second = vaultCrypto.wrapMasterKey(masterKey, encryptionKey) as Outcome.Success
        assertFalse(first.value.contentEquals(second.value))
    }

    @Test
    fun `given a wrapped master key and wrong encryption key, when unwrapping, then returns decryption failed error`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val correctKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val wrongKey = ByteArray(MASTER_KEY_SIZE) { (it + 2).toByte() }
        val wrapped = vaultCrypto.wrapMasterKey(masterKey, correctKey) as Outcome.Success
        val result = vaultCrypto.unwrapMasterKey(wrapped.value, wrongKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.DecryptionFailed)
    }

    @Test
    fun `given corrupted wrapped master key, when unwrapping, then returns decryption failed error`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val encryptionKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val wrapped = (vaultCrypto.wrapMasterKey(masterKey, encryptionKey) as Outcome.Success).value
        val corrupted = wrapped.copyOf()
        corrupted[wrapped.size - 1] = (corrupted[wrapped.size - 1] + 1).toByte()
        val result = vaultCrypto.unwrapMasterKey(corrupted, encryptionKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.DecryptionFailed)
    }

    @Test
    fun `given truncated wrapped master key, when unwrapping, then returns malformed data error`() {
        val tooShort = ByteArray(MINIMUM_SEALED_SIZE - 1) { it.toByte() }
        val encryptionKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val result = vaultCrypto.unwrapMasterKey(tooShort, encryptionKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.MalformedData)
    }

    @Test
    fun `given a master key of wrong size, when wrapping, then returns invalid key size error`() {
        val wrongSizeMasterKey = ByteArray(MASTER_KEY_SIZE + 1) { it.toByte() }
        val encryptionKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val result = vaultCrypto.wrapMasterKey(wrongSizeMasterKey, encryptionKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidKeySize)
    }

    @Test
    fun `given an encryption key of wrong size, when wrapping, then returns invalid key size error`() {
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val wrongSizeEncryptionKey = ByteArray(MASTER_KEY_SIZE + 1) { (it + 1).toByte() }
        val result = vaultCrypto.wrapMasterKey(masterKey, wrongSizeEncryptionKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidKeySize)
    }

    @Test
    fun `given an encryption key of wrong size, when unwrapping, then returns invalid key size error`() {
        val wrappedMasterKey = ByteArray(MINIMUM_SEALED_SIZE) { it.toByte() }
        val wrongSizeEncryptionKey = ByteArray(MASTER_KEY_SIZE + 1) { (it + 1).toByte() }
        val result = vaultCrypto.unwrapMasterKey(wrappedMasterKey, wrongSizeEncryptionKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidKeySize)
    }

    @Test
    fun `given plaintext and a valid master key, when encrypting, then returns sealed bytes`() {
        val plaintext = "hello world".toByteArray()
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val result = vaultCrypto.encrypt(plaintext, masterKey)
        assertTrue(result is Outcome.Success)
        assertTrue((result as Outcome.Success).value.isNotEmpty())
    }

    @Test
    fun `given sealed bytes and the correct master key, when decrypting, then recovers the original plaintext`() {
        val plaintext = "hello world".toByteArray()
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val encrypted = vaultCrypto.encrypt(plaintext, masterKey) as Outcome.Success
        val decrypted = vaultCrypto.decrypt(encrypted.value, masterKey) as Outcome.Success
        assertArrayEquals(plaintext, decrypted.value)
    }

    @Test
    fun `given empty plaintext, when encrypting and decrypting, then round-trips to empty`() {
        val plaintext = ByteArray(0)
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val encrypted = vaultCrypto.encrypt(plaintext, masterKey) as Outcome.Success
        val decrypted = vaultCrypto.decrypt(encrypted.value, masterKey) as Outcome.Success
        assertArrayEquals(plaintext, decrypted.value)
    }

    @Test
    fun `given the same plaintext, when encrypting twice, then produces different outputs`() {
        val plaintext = "hello world".toByteArray()
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val first = vaultCrypto.encrypt(plaintext, masterKey) as Outcome.Success
        val second = vaultCrypto.encrypt(plaintext, masterKey) as Outcome.Success
        assertFalse(first.value.contentEquals(second.value))
    }

    @Test
    fun `given sealed bytes and a wrong master key, when decrypting, then returns decryption failed error`() {
        val plaintext = "hello world".toByteArray()
        val correctKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val wrongKey = ByteArray(MASTER_KEY_SIZE) { (it + 1).toByte() }
        val encrypted = vaultCrypto.encrypt(plaintext, correctKey) as Outcome.Success
        val result = vaultCrypto.decrypt(encrypted.value, wrongKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.DecryptionFailed)
    }

    @Test
    fun `given corrupted sealed bytes, when decrypting, then returns decryption failed error`() {
        val plaintext = "hello world".toByteArray()
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val encrypted = (vaultCrypto.encrypt(plaintext, masterKey) as Outcome.Success).value
        val corrupted = encrypted.copyOf()
        corrupted[encrypted.size - 1] = (corrupted[encrypted.size - 1] + 1).toByte()
        val result = vaultCrypto.decrypt(corrupted, masterKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.DecryptionFailed)
    }

    @Test
    fun `given truncated sealed bytes, when decrypting, then returns malformed data error`() {
        val tooShort = ByteArray(MINIMUM_SEALED_SIZE - 1) { it.toByte() }
        val masterKey = ByteArray(MASTER_KEY_SIZE) { it.toByte() }
        val result = vaultCrypto.decrypt(tooShort, masterKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.MalformedData)
    }

    @Test
    fun `given a master key of wrong size, when encrypting, then returns invalid key size error`() {
        val plaintext = "hello world".toByteArray()
        val wrongSizeKey = ByteArray(MASTER_KEY_SIZE + 1) { it.toByte() }
        val result = vaultCrypto.encrypt(plaintext, wrongSizeKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidKeySize)
    }

    @Test
    fun `given a master key of wrong size, when decrypting, then returns invalid key size error`() {
        val ciphertext = ByteArray(MINIMUM_SEALED_SIZE) { it.toByte() }
        val wrongSizeKey = ByteArray(MASTER_KEY_SIZE + 1) { it.toByte() }
        val result = vaultCrypto.decrypt(ciphertext, wrongSizeKey)
        assertTrue(result is Outcome.Failure)
        assertTrue((result as Outcome.Failure).error is CryptoError.InvalidKeySize)
    }

    @Test
    fun `given a wrapped master key, when re-deriving keys and unwrapping, then recovers the master key`() {
        val password = "correcthorsebatterystaple"
        val salt = ByteArray(SALT_SIZE) { it.toByte() }
        val masterKey = vaultCrypto.generateMasterKey()
        val derivedKeys = (vaultCrypto.deriveKeys(password, salt) as Outcome.Success).value
        val wrapped = (vaultCrypto.wrapMasterKey(masterKey, derivedKeys.encryptionKey) as Outcome.Success).value
        val reDerivedKeys = (vaultCrypto.deriveKeys(password, salt) as Outcome.Success).value
        val unwrapped = (vaultCrypto.unwrapMasterKey(wrapped, reDerivedKeys.encryptionKey) as Outcome.Success).value
        assertArrayEquals(masterKey, unwrapped)
        assertEquals(derivedKeys.authHash, reDerivedKeys.authHash)
    }
}
