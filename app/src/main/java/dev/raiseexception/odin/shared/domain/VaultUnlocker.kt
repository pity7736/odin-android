package dev.raiseexception.odin.shared.domain

interface VaultUnlocker {
    suspend fun unlock(encryptionKey: ByteArray): Outcome<Unit>
    suspend fun discard(): Outcome<Unit>
}
