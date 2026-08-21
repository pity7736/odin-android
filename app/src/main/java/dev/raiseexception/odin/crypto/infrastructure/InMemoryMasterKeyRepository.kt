package dev.raiseexception.odin.crypto.infrastructure

import dev.raiseexception.odin.crypto.domain.CryptoError
import dev.raiseexception.odin.crypto.domain.repository.MasterKeyRepository
import dev.raiseexception.odin.shared.domain.Outcome
import java.util.concurrent.atomic.AtomicReference

class InMemoryMasterKeyRepository : MasterKeyRepository {

    private val masterKeyReference = AtomicReference<ByteArray?>(null)

    override fun store(masterKey: ByteArray) {
        masterKeyReference.set(masterKey.copyOf())
    }

    override fun get(): Outcome<ByteArray> {
        val masterKey = masterKeyReference.get()
            ?: return Outcome.Failure(CryptoError.MasterKeyNotFound())
        return Outcome.Success(masterKey.copyOf())
    }

    override fun clear() {
        masterKeyReference.set(null)
    }
}
