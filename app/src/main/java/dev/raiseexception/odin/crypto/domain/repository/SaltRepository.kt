package dev.raiseexception.odin.crypto.domain.repository

import dev.raiseexception.odin.shared.domain.Outcome

interface SaltRepository {
    suspend fun save(salt: ByteArray): Outcome<Unit>
    suspend fun get(): Outcome<ByteArray>
    suspend fun exists(): Boolean
    suspend fun delete()
}
