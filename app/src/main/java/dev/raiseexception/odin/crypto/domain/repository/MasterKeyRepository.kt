package dev.raiseexception.odin.crypto.domain.repository

import dev.raiseexception.odin.shared.domain.Outcome

interface MasterKeyRepository {
    fun store(masterKey: ByteArray)
    fun get(): Outcome<ByteArray>
    fun clear()
}
