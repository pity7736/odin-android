package dev.raiseexception.odin.accounting.infrastructure.repository

import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.shared.domain.Outcome

internal class FakeMasterKeyRepository(private var masterKey: ByteArray?) : MasterKeyRepository {

    override fun store(masterKey: ByteArray) {
        this.masterKey = masterKey
    }

    override fun get(): Outcome<ByteArray> {
        val key = this.masterKey ?: return Outcome.Failure(CryptoError.MasterKeyNotFound())
        return Outcome.Success(key)
    }

    override fun clear() {
        this.masterKey = null
    }
}
