package dev.raiseexception.odin.shared.infrastructure.vault

import dev.raiseexception.odin.shared.domain.Outcome

interface EncryptedRecordStore {
    suspend fun save(id: String, plaintext: ByteArray): Outcome<Unit>
    suspend fun readAll(): Outcome<List<StoredRecord>>
}
