package dev.raiseexception.odin.crypto.infrastructure

import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.DerivedKeys
import dev.raiseexception.odin.crypto.domain.VaultCrypto
import dev.raiseexception.odin.shared.domain.Outcome
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BouncyCastleVaultCrypto(
    private val secureRandom: SecureRandom = SecureRandom()
) : VaultCrypto {

    override fun deriveKeys(password: String, salt: ByteArray): Outcome<DerivedKeys> = when {
        password.isEmpty() -> Outcome.Failure(CryptoError.InvalidPassword())
        salt.size != SALT_SIZE -> Outcome.Failure(CryptoError.InvalidSalt())
        else -> deriveKeysFromArgon2id(password, salt)
    }

    override fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        secureRandom.nextBytes(salt)
        return salt
    }

    private fun deriveKeysFromArgon2id(password: String, salt: ByteArray): Outcome<DerivedKeys> {
        val output = ByteArray(VaultCrypto.ARGON_OUTPUT_LENGTH)
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(VaultCrypto.ARGON_VERSION)
            .withIterations(VaultCrypto.ARGON_ITERATIONS)
            .withMemoryAsKB(VaultCrypto.ARGON_MEMORY)
            .withParallelism(VaultCrypto.ARGON_PARALLELISM)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(parameters)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), output)
        val authHash = Base64.getEncoder().encodeToString(output.copyOfRange(0, ENCRYPTION_KEY_SIZE))
        val encryptionKey = output.copyOfRange(ENCRYPTION_KEY_SIZE, VaultCrypto.ARGON_OUTPUT_LENGTH)
        return Outcome.Success(DerivedKeys(authHash = authHash, encryptionKey = encryptionKey))
    }

    override fun generateMasterKey(): ByteArray {
        val key = ByteArray(MASTER_KEY_SIZE)
        secureRandom.nextBytes(key)
        return key
    }

    override fun wrapMasterKey(masterKey: ByteArray, encryptionKey: ByteArray): Outcome<ByteArray> = when {
        masterKey.size != MASTER_KEY_SIZE -> Outcome.Failure(CryptoError.InvalidKeySize())
        encryptionKey.size != ENCRYPTION_KEY_SIZE -> Outcome.Failure(CryptoError.InvalidKeySize())
        else -> aesGcmEncrypt(masterKey, encryptionKey)
    }

    override fun unwrapMasterKey(wrappedMasterKey: ByteArray, encryptionKey: ByteArray): Outcome<ByteArray> {
        if (encryptionKey.size != ENCRYPTION_KEY_SIZE) {
            return Outcome.Failure(CryptoError.InvalidKeySize())
        }
        return aesGcmDecrypt(wrappedMasterKey, encryptionKey)
    }

    override fun encrypt(plaintext: ByteArray, masterKey: ByteArray): Outcome<ByteArray> {
        if (masterKey.size != MASTER_KEY_SIZE) {
            return Outcome.Failure(CryptoError.InvalidKeySize())
        }
        return aesGcmEncrypt(plaintext, masterKey)
    }

    override fun decrypt(ciphertext: ByteArray, masterKey: ByteArray): Outcome<ByteArray> {
        if (masterKey.size != MASTER_KEY_SIZE) {
            return Outcome.Failure(CryptoError.InvalidKeySize())
        }
        return aesGcmDecrypt(ciphertext, masterKey)
    }

    private fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray): Outcome<ByteArray> {
        val nonce = ByteArray(GCM_NONCE_SIZE)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val keySpec = SecretKeySpec(key, AES_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_BIT_LENGTH, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertextWithTag = cipher.doFinal(plaintext)
        val result = ByteArray(nonce.size + ciphertextWithTag.size)
        nonce.copyInto(result)
        ciphertextWithTag.copyInto(result, nonce.size)
        return Outcome.Success(result)
    }

    private fun aesGcmDecrypt(sealedBytes: ByteArray, key: ByteArray): Outcome<ByteArray> {
        if (sealedBytes.size < GCM_NONCE_SIZE + GCM_TAG_BYTE_LENGTH) {
            return Outcome.Failure(CryptoError.MalformedData())
        }
        return try {
            val nonce = sealedBytes.copyOfRange(0, GCM_NONCE_SIZE)
            val ciphertextWithTag = sealedBytes.copyOfRange(GCM_NONCE_SIZE, sealedBytes.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(key, AES_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_BIT_LENGTH, nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            Outcome.Success(cipher.doFinal(ciphertextWithTag))
        } catch (@Suppress("SwallowedException") exception: AEADBadTagException) {
            Outcome.Failure(CryptoError.DecryptionFailed())
        }
    }

    private companion object {
        const val SALT_SIZE = 16
        const val MASTER_KEY_SIZE = 32
        const val ENCRYPTION_KEY_SIZE = 32
        const val GCM_NONCE_SIZE = 12
        const val GCM_TAG_BIT_LENGTH = 128
        const val GCM_TAG_BYTE_LENGTH = 16
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_ALGORITHM = "AES"
    }
}
