package dev.raiseexception.odin.shared.domain

interface VaultUnlocker {
    fun unlock(encryptionKey: ByteArray): Outcome<Unit>
}
