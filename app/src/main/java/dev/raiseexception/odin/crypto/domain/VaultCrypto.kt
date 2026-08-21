package dev.raiseexception.odin.crypto.domain

import dev.raiseexception.odin.shared.domain.Outcome

interface VaultCrypto {
    fun deriveKeys(password: String, salt: ByteArray): Outcome<DerivedKeys>
    fun generateMasterKey(): ByteArray
    fun wrapMasterKey(masterKey: ByteArray, encryptionKey: ByteArray): Outcome<ByteArray>
    fun unwrapMasterKey(wrappedMasterKey: ByteArray, encryptionKey: ByteArray): Outcome<ByteArray>
    fun encrypt(plaintext: ByteArray, masterKey: ByteArray): Outcome<ByteArray>
    fun decrypt(ciphertext: ByteArray, masterKey: ByteArray): Outcome<ByteArray>
}

data class DerivedKeys(
    val authHash: String,
    val encryptionKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DerivedKeys) return false
        return authHash == other.authHash && encryptionKey.contentEquals(other.encryptionKey)
    }

    override fun hashCode(): Int {
        var result = authHash.hashCode()
        result = HASH_MULTIPLIER * result + encryptionKey.contentHashCode()
        return result
    }

    override fun toString(): String = "DerivedKeys(authHash=***, encryptionKey=***)"

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}
